package edu.course.library.service;

import edu.course.library.config.LibrarySettings;
import edu.course.library.mapper.LoanMapper;
import edu.course.library.mapper.ReaderMapper;
import edu.course.library.model.Reader;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** R005：从未归还的逾期记录动态计算状态；旧库无人工冻结标记，FROZEN 默认持续直至管理员解冻。 */
@Service
public class OverdueService {
    private final ReaderMapper readers;
    private final LoanMapper loans;
    private final Clock clock;
    private final LibrarySettings settings;
    public OverdueService(ReaderMapper readers,LoanMapper loans,Clock clock,LibrarySettings settings){
        this.readers=readers;this.loans=loans;this.clock=clock;this.settings=settings;
    }
    /**
     * 必须在已经持有读者 FOR UPDATE 行锁的事务里执行。
     * D-01 待确认：未还逾期全部结清可恢复 NORMAL；FROZEN 持续至管理员主动解冻。
     */
    public String recomputeUnderLock(Reader reader){
        if ("INACTIVE".equals(reader.status())) return "INACTIVE";
        // 真实 library_db 没有人工冻结标志，无法区分长期冻结的来源；安全地保留现有 FROZEN。
        if ("FROZEN".equals(reader.status())) return "FROZEN";
        int days=loans.currentMaxOverdueDays(reader.id(),LocalDate.now(clock));
        String expected=days>settings.freezeDays?"FROZEN":days>0?"RESTRICTED":"NORMAL";
        if(!reader.status().equals(expected)) readers.setComputedStatus(reader.id(),expected);
        return expected;
    }
    /** 定时调度调用的是这个独立 public 方法，确保事务代理实际生效。 */
    @Transactional
    public void refreshReader(long id){
        Reader reader=readers.lockById(id);
        if(reader!=null)recomputeUnderLock(reader);
    }
}
