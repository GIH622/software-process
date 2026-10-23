package edu.course.library.service;

import edu.course.library.TestSupport;
import edu.course.library.exception.BusinessException;
import edu.course.library.mapper.BookMapper;
import edu.course.library.mapper.LoanMapper;
import edu.course.library.mapper.ReaderMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** H2 + Spring 真正事务集成测试；MySQL 并发由脚本单独验收。 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestSupport.class)
class BorrowServiceIntegrationTest {
    @Autowired BorrowService service;
    @Autowired ReaderMapper readers;
    @Autowired BookMapper books;
    @Autowired LoanMapper loans;
    @Autowired OverdueService overdueService;
    @Autowired AdminService adminService;
    @Autowired JdbcTemplate jdbc;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired TestSupport.MutableProbe probe;
    @BeforeEach void reset(){probe.reset();TestSupport.resetDatabase(jdbc,encoder);}
    private int number(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    private long borrow(){return service.borrow("1001","BC-0001","1001","READER").recordId();}
    @Test void tc001_normalBorrowUpdatesAllThreeTables(){
        var r=service.borrow("1001","BC-0001","1001","READER");
        assertEquals("BORROWED",r.status());assertEquals(LocalDate.of(2026,11,19),r.dueDate());
        assertEquals(1,number("SELECT borrowed_count FROM reader_account WHERE card_no='1001'"));
        assertEquals(1,number("SELECT COUNT(*) FROM borrow_record WHERE status='BORROWED'"));
        assertEquals("BORROWED",books.findByBarcode("BC-0001").status());
    }
    @Test void tc002_limitBlocksExtraBorrow(){
        jdbc.update("UPDATE reader_account SET borrow_limit=1 WHERE card_no='1001'");borrow();
        assertEquals("BORROW_LIMIT",assertThrows(BusinessException.class,
            ()->service.borrow("1001","BC-0003","1001","READER")).errorCode());
    }
    @Test void tc003_unavailableBookIsRejected(){
        borrow();assertEquals("BOOK_UNAVAILABLE",assertThrows(BusinessException.class,
            ()->service.borrow("1002","BC-0001","1002","READER")).errorCode());
    }
    @Test void tc004_frozenReaderCannotBorrow(){
        jdbc.update("UPDATE reader_account SET status='FROZEN' WHERE card_no='1001'");
        assertEquals("BORROW_RESTRICTED",assertThrows(BusinessException.class,()->borrow()).errorCode());
    }
    @Test void tc005_borrowFaultRollsBackAllWrites(){
        probe.breakBorrow.set(true);
        assertThrows(IllegalStateException.class,()->borrow());
        assertEquals("AVAILABLE",books.findByBarcode("BC-0001").status());
        assertEquals(0,number("SELECT COUNT(*) FROM borrow_record"));
        assertEquals(0,number("SELECT borrowed_count FROM reader_account WHERE card_no='1001'"));
        assertEquals(0,number("SELECT COUNT(*) FROM operation_log"));
    }
    @Test void tc006_returnUpdatesAllThreeTables(){
        borrow();var r=service.returnBook("BC-0001","1001","READER");
        assertEquals("RETURNED",r.status());assertEquals(0,r.overdueDays());
        assertEquals("AVAILABLE",books.findByBarcode("BC-0001").status());
        assertEquals(0,number("SELECT borrowed_count FROM reader_account WHERE card_no='1001'"));
    }
    @Test void tc007_returnComputesOverdueDaysButNotFine(){
        borrow();jdbc.update("UPDATE borrow_record SET expected_return_date=? WHERE status='BORROWED'",LocalDate.of(2026,10,17));
        var result=service.returnBook("BC-0001","1001","READER");
        assertEquals(3,result.overdueDays());
        assertEquals(3,number("SELECT overdue_days FROM borrow_record WHERE borrow_record_id=?",result.recordId()));
        // D-01 待批准口径：逾期还清且无其他借阅，恢复 NORMAL。
        assertEquals("NORMAL",result.readerStatus());
    }
    @Test void tc008_duplicateReturnRejected(){
        borrow();service.returnBook("BC-0001","1001","READER");
        assertEquals("NOT_BORROWED",assertThrows(BusinessException.class,
            ()->service.returnBook("BC-0001","1001","READER")).errorCode());
    }
    @Test void tc009_otherReaderCannotReturn(){
        borrow();assertEquals("NOT_YOUR_LOAN",assertThrows(BusinessException.class,
            ()->service.returnBook("BC-0001","1002","READER")).errorCode());
    }
    @Test void tc010_returnFaultRollsBackAllWrites(){
        borrow();probe.breakReturn.set(true);
        assertThrows(IllegalStateException.class,()->service.returnBook("BC-0001","1001","READER"));
        assertEquals("BORROWED",books.findByBarcode("BC-0001").status());
        assertEquals(1,number("SELECT borrowed_count FROM reader_account WHERE card_no='1001'"));
        assertEquals(1,number("SELECT COUNT(*) FROM borrow_record WHERE status='BORROWED'"));
    }
    @Test void tc011_sameIsbnSameDayCannotBeBorrowedTwice(){
        borrow();assertEquals("DUPLICATE_BORROW",assertThrows(BusinessException.class,
            ()->service.borrow("1001","BC-0002","1001","READER")).errorCode());
    }
    @Test void tc012_unknownReaderRejected(){
        assertEquals("READER_NOT_FOUND",assertThrows(BusinessException.class,
            ()->service.borrow("9999","BC-0001","9999","READER")).errorCode());
    }
    @Test void tc013_unknownBookRejected(){
        assertEquals("BOOK_NOT_FOUND",assertThrows(BusinessException.class,
            ()->service.borrow("1001","BC-NOPE","1001","READER")).errorCode());
    }
    @Test void tc014_oneDayOverdueBlocksNewLoans(){
        borrow();jdbc.update("UPDATE borrow_record SET expected_return_date=? WHERE status='BORROWED'",LocalDate.of(2026,10,19));
        assertEquals("BORROW_RESTRICTED",assertThrows(BusinessException.class,
            ()->service.borrow("1001","BC-0003","1001","READER")).errorCode());
        // 拒绝借阅会回滚当前事务的状态写入；每天定时任务有自己的提交事务。
        overdueService.refreshReader(readers.findByCard("1001").id());
        assertEquals("RESTRICTED",readers.findByCard("1001").status());
    }
    @Test void tc015_thirtySixDaysOverdueFreezes(){
        borrow();jdbc.update("UPDATE borrow_record SET expected_return_date=?",LocalDate.of(2026,9,14));
        assertEquals("BORROW_RESTRICTED",assertThrows(BusinessException.class,
            ()->service.borrow("1001","BC-0003","1001","READER")).errorCode());
        // 上面的异常导致整个事务回滚，读者状态字段不应被错误地“持久化成功”；
        // 定时任务单独事务刷新状态后应为 FROZEN。
        assertEquals("NORMAL",readers.findByCard("1001").status());
        overdueService.refreshReader(readers.findByCard("1001").id());
        assertEquals("FROZEN",readers.findByCard("1001").status());
    }
    @Test void tc016_anAdminMayReturnSomeoneElsesBook(){
        borrow();assertEquals("RETURNED",service.returnBook("BC-0001","testadmin","ADMIN").status());
    }
    @Test void tc017_afterLateReturnStillRestrictWhenAnotherBookIsOverdue(){
        borrow();service.borrow("1001","BC-0003","1001","READER");
        jdbc.update("UPDATE borrow_record SET expected_return_date=?",LocalDate.of(2026,10,17));
        var result=service.returnBook("BC-0001","1001","READER");
        assertEquals("RESTRICTED",result.readerStatus());
    }
    @Test void tc018_successAuditLogIsPersisted(){
        borrow();service.returnBook("BC-0001","1001","READER");
        assertEquals(2,number("SELECT COUNT(*) FROM operation_log"));
    }

    /**
     * 团队需求分析 2026-10-20 现行口径：逾期第 35 天仍是限制借阅；
     * 这与部分旧版 SRS 中的“超过 30 天”存在差异，正式合并前须完成需求评审。
     */
    @Test void tc031_thirtyFiveDaysOverdueStaysRestricted(){
        borrow();
        jdbc.update("UPDATE borrow_record SET expected_return_date=? WHERE status='BORROWED'",
            LocalDate.of(2026,9,15));
        long id=readers.findByCard("1001").id();
        overdueService.refreshReader(id);
        assertEquals("RESTRICTED",readers.findByCard("1001").status());
    }

    /** 严重逾期超过 35 天后保持 FROZEN，只有管理员核实后执行解冻。 */
    @Test void tc032_frozenAfterReturnNeedsAdministratorUnfreeze(){
        borrow();
        jdbc.update("UPDATE borrow_record SET expected_return_date=? WHERE status='BORROWED'",
            LocalDate.of(2026,9,14));
        long id=readers.findByCard("1001").id();
        overdueService.refreshReader(id);
        assertEquals("FROZEN",readers.findByCard("1001").status());
        var result=service.returnBook("BC-0001","1001","READER");
        assertEquals("FROZEN",result.readerStatus());
        assertEquals("FROZEN",readers.findByCard("1001").status());
        assertEquals("NORMAL",adminService.setFreeze(id,false,"testadmin"));
        assertEquals("NORMAL",readers.findByCard("1001").status());
    }

    /**
     * 回归：定时任务尚未刷新状态时，直接归还一本逾期 36 天的书也必须冻结。
     * 旧实现只统计剩余 BORROWED 记录，刚归还的记录已被排除，读者可借此逃避冻结。
     */
    @Test void tc033_returnOverdueBookFreezesWithoutSchedulerRefresh(){
        borrow();
        jdbc.update("UPDATE borrow_record SET expected_return_date=? WHERE status='BORROWED'",
            LocalDate.of(2026,9,14));
        assertEquals("NORMAL",readers.findByCard("1001").status());
        var result=service.returnBook("BC-0001","1001","READER");
        assertEquals(36,result.overdueDays());
        assertEquals("FROZEN",result.readerStatus());
        assertEquals("FROZEN",readers.findByCard("1001").status());
    }
}
