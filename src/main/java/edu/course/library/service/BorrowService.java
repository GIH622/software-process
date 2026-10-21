package edu.course.library.service;

import edu.course.library.config.LibrarySettings;
import edu.course.library.dto.Forms;
import edu.course.library.exception.BusinessException;
import edu.course.library.mapper.*;
import edu.course.library.model.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * R001 借阅 + R002 归还核心实现（强事务、高可靠性）。
 * 关键约束：所有操作都按“读者行 → 图书副本行 → 借阅记录行”的顺序获得行锁，
 * 同一事务同时更新读者借阅数量、副本状态、借阅记录及成功操作日志。
 * 任一步失败抛 RuntimeException：Spring 默认自动回滚，禁止吞掉异常后返回成功。
 * R003 原始库含“批准”记录但缺实施/验证；默认关闭，仅确认后开通并迁移字段。
 */
@Service
public class BorrowService {
    private final ReaderMapper readers;
    private final BookMapper books;
    private final LoanMapper loans;
    private final OperationLogMapper logs;
    private final OverdueService overdue;
    private final MutationProbe probe;
    private final LibrarySettings settings;
    private final Clock clock;
    public BorrowService(ReaderMapper readers,BookMapper books,LoanMapper loans,
                         OperationLogMapper logs,OverdueService overdue,MutationProbe probe,
                         LibrarySettings settings,Clock clock){
        this.readers=readers;this.books=books;this.loans=loans;this.logs=logs;
        this.overdue=overdue;this.probe=probe;this.settings=settings;this.clock=clock;
    }
    private static BusinessException fail(HttpStatus s,String c,String msg){return new BusinessException(s,c,msg);}
    private static void ensureActor(String role,String actorCard,String owner){
        if(!"ADMIN".equals(role)&&!actorCard.equals(owner))
            throw fail(HttpStatus.FORBIDDEN,"NOT_YOUR_LOAN","读者只能操作自己的借阅记录");
    }
    /**
     * R001 / AC-1~AC-6：检查登录人、读者状态、借阅上限与馆藏状态，再原子更新三张业务表。
     * READ_COMMITTED 配合行锁和数据库唯一索引，避免并发下双重借出。
     */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Forms.Receipt borrow(String cardNo,String barcode,String actorCard,String role){
        ensureActor(role,actorCard,cardNo);
        Reader reader=readers.lockByCard(cardNo);
        if(reader==null)throw fail(HttpStatus.NOT_FOUND,"READER_NOT_FOUND","读者不存在");
        String readerStatus=overdue.recomputeUnderLock(reader);
        if(!"NORMAL".equals(readerStatus))throw fail(HttpStatus.FORBIDDEN,"BORROW_RESTRICTED","读者当前不可借阅："+readerStatus);
        if(reader.borrowedCount()>=reader.borrowLimit())
            throw fail(HttpStatus.CONFLICT,"BORROW_LIMIT","已达最大同时借阅数量");
        BookCopy snapshot=books.findByBarcode(barcode);
        if(snapshot==null)throw fail(HttpStatus.NOT_FOUND,"BOOK_NOT_FOUND","未找到图书条码");
        BookCopy book=books.lockById(snapshot.id());
        if(!"AVAILABLE".equals(book.status()))
            throw fail(HttpStatus.CONFLICT,"BOOK_UNAVAILABLE","该图书当前无可借库存");
        LocalDate today=LocalDate.now(clock);
        if(loans.hasBorrowedSameIsbnToday(reader.id(),book.isbn(),today))
            throw fail(HttpStatus.CONFLICT,"DUPLICATE_BORROW","同一读者当日已登记同一本ISBN的借阅");
        LocalDateTime now=LocalDateTime.now(clock);
        LocalDate due=today.plusDays(settings.borrowDays);
        if(books.markBorrowed(book.id())!=1)throw new IllegalStateException("更新副本占用状态失败");
        long recordId=loans.insert(reader.id(),book.id(),now,due);
        if(readers.changeBorrowedCount(reader.id(),1)!=1)
            throw new IllegalStateException("递增读者借阅计数失败，事务必须回滚");
        // 故障注入专门证明：即使前三步已完成，以下异常也会撤销全部数据库写入。
        probe.afterBorrowWrites();
        logs.insert("BORROW",actorCard,reader.id(),book.isbn(),"R001 借阅登记，barcode="+barcode);
        return new Forms.Receipt(recordId,cardNo,barcode,book.title(),"BORROWED",today,due,null,0,readerStatus);
    }
    /**
     * R003 / CR-2026-1020-001：待执行续借迁移且由组长授权开启后方可使用。
     * 该审批记录存在于上传的 SQL 备份，但实际实施/验证日期仍为空，默认禁用。
     * 输入为借阅记录主键；续借不得发生在已经逾期、已归还或超次数的记录上。
     */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Forms.Receipt renew(long recordId,String actorCard,String role){
        if(!settings.renewalEnabled)
            throw fail(HttpStatus.CONFLICT,"RENEWAL_DISABLED","R003 续借暂未开通，请先完成变更审批确认和数据库迁移");
        Loan snapshot=loans.findById(recordId);
        if(snapshot==null)throw fail(HttpStatus.NOT_FOUND,"LOAN_NOT_FOUND","借阅记录不存在");
        Reader reader=readers.lockById(snapshot.readerId());
        if(reader==null)throw fail(HttpStatus.CONFLICT,"DATA_CONFLICT","关联读者不存在");
        ensureActor(role,actorCard,reader.cardNo());
        BookCopy book=books.lockById(snapshot.bookId());
        Loan loan=loans.lockById(recordId);
        if(book==null || loan==null || loan.readerId()!=reader.id() || loan.bookId()!=book.id() ||
            !"BORROWED".equals(loan.status()) || !"BORROWED".equals(book.status()))
            throw fail(HttpStatus.CONFLICT,"RENEWAL_NOT_ACTIVE","该借阅记录已归还或状态异常");
        LocalDate today=LocalDate.now(clock);
        if(loan.dueDate().isBefore(today))
            throw fail(HttpStatus.CONFLICT,"RENEWAL_OVERDUE","已逾期记录不能续借");
        String currentStatus=overdue.recomputeUnderLock(reader);
        if(!"NORMAL".equals(currentStatus))
            throw fail(HttpStatus.FORBIDDEN,"RENEWAL_RESTRICTED","当前读者处于限制/冻结状态");
        int renewCount=loans.renewalCount(recordId); // 该列仅在运行过CR迁移后存在。
        if(renewCount>=settings.maxRenewals)
            throw fail(HttpStatus.CONFLICT,"RENEWAL_LIMIT","已达到续借次数上限");
        LocalDate newDue=loan.dueDate().plusDays(settings.renewalDays);
        if(loans.extendDueDate(recordId,settings.maxRenewals,today,newDue)!=1)
            throw fail(HttpStatus.CONFLICT,"RENEWAL_CONFLICT","续借冲突或状态变化，请刷新后重试");
        logs.insert("RENEW",actorCard,reader.id(),book.isbn(),
            "CR-2026-1020-001 续借，barcode="+book.barcode()+"，原到期="+loan.dueDate());
        return new Forms.Receipt(recordId,reader.cardNo(),book.barcode(),book.title(),
            "BORROWED",loan.borrowTime().toLocalDate(),newDue,null,0,currentStatus);
    }
    /**
     * R002 / AC-7~AC-12：通过唯一条码定位活动借阅；执行者可为借阅人或管理员。
     * 不计算罚金（新 SRS 明确排除收费），只计算 overdueDays 和剩余借阅的逾期状态。
     */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Forms.Receipt returnBook(String barcode,String actorCard,String role){
        BookCopy snapshot=books.findByBarcode(barcode);
        if(snapshot==null)throw fail(HttpStatus.NOT_FOUND,"BOOK_NOT_FOUND","未找到图书条码");
        Loan preliminary=loans.findActiveByBookId(snapshot.id());
        if(preliminary==null)throw fail(HttpStatus.CONFLICT,"NOT_BORROWED","该图书无未归还记录");
        Reader reader=readers.lockById(preliminary.readerId());
        if(reader==null)throw new IllegalStateException("借阅记录关联读者丢失");
        ensureActor(role,actorCard,reader.cardNo());
        BookCopy book=books.lockById(snapshot.id());
        Loan loan=loans.lockById(preliminary.id());
        if(loan==null || !"BORROWED".equals(loan.status()) || !"BORROWED".equals(book.status()))
            throw fail(HttpStatus.CONFLICT,"ALREADY_RETURNED","该图书已归还或状态不一致");
        LocalDate today=LocalDate.now(clock);
        int days=(int)Math.max(0,ChronoUnit.DAYS.between(loan.dueDate(),today));
        LocalDateTime now=LocalDateTime.now(clock);
        if(loans.markReturned(loan.id(),now,days)!=1)throw new IllegalStateException("更新归还记录失败");
        if(books.markAvailable(book.id())!=1)throw new IllegalStateException("释放副本失败");
        if(readers.changeBorrowedCount(reader.id(),-1)!=1)throw new IllegalStateException("递减读者计数失败");
        // 状态依据“剩余未归还记录”重新计算：无欠还自动恢复；人工冻结则不自动解除。
        String status=overdue.recomputeUnderLock(reader);
        probe.afterReturnWrites();
        logs.insert("RETURN",actorCard,reader.id(),book.isbn(),"R002 归还，barcode="+barcode+"，逾期"+days+"天");
        return new Forms.Receipt(loan.id(),reader.cardNo(),barcode,book.title(),"RETURNED",
            loan.borrowTime().toLocalDate(),loan.dueDate(),today,days,status);
    }
}
