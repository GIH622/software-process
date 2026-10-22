package edu.course.library.service;

import edu.course.library.config.LibrarySettings;
import edu.course.library.mapper.LoanMapper;
import edu.course.library.mapper.ReaderMapper;
import edu.course.library.model.Reader;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** R005：从未归还的逾期记录动态计算状态；FROZEN 持续至管理员解冻，解冻后按剩余逾期重新定级。 */
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
    public String recomputeUnderLock(Reader reader){return recomputeUnderLock(reader,0);}
    /**
     * 归还时使用：刚归还的记录已变为 RETURNED，不再计入剩余未还记录。
     * 轻度逾期还清后按 D-01 恢复（只看剩余未还记录）；严重逾期（>freezeDays）必须冻结，
     * 否则读者可在定时任务刷新前还书逃避冻结。
     */
    public String recomputeUnderLock(Reader reader,int justReturnedOverdueDays){
        if ("INACTIVE".equals(reader.status())) return "INACTIVE";
        // 数据库没有冻结来源标记，无法区分手动/自动冻结；FROZEN 一律保持到管理员解冻，避免自动恢复。
        if ("FROZEN".equals(reader.status())) return "FROZEN";
        int remainingDays=loans.currentMaxOverdueDays(reader.id(),LocalDate.now(clock));
        return applyStatus(reader,justReturnedOverdueDays>settings.freezeDays?justReturnedOverdueDays:remainingDays);
    }
    /**
     * 管理员解冻专用：显式跳过 FROZEN 粘滞，按剩余未还逾期重新定级。
     * 若读者仍欠超过 freezeDays 的逾期，会立即回到 FROZEN——解冻不等于放行。
     * 必须在已持有读者行锁的事务内调用。
     */
    public String recomputeAfterUnfreeze(Reader reader){
        if ("INACTIVE".equals(reader.status())) return "INACTIVE";
        return applyStatus(reader,loans.currentMaxOverdueDays(reader.id(),LocalDate.now(clock)));
    }
    private String applyStatus(Reader reader,int days){
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
