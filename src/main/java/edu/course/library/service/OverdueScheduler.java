package edu.course.library.service;

import edu.course.library.mapper.ReaderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天北京时间 00:10 刷新仍在借阅的逾期读者，借阅请求前也会即时刷新一次。 */
@Component
public class OverdueScheduler {
    private static final Logger log=LoggerFactory.getLogger(OverdueScheduler.class);
    private final ReaderMapper readers;
    private final OverdueService overdue;
    public OverdueScheduler(ReaderMapper readers,OverdueService overdue){this.readers=readers;this.overdue=overdue;}
    @Scheduled(cron="0 10 0 * * *",zone="Asia/Shanghai")
    public void refreshAll(){
        for(Long readerId:readers.findIds()){
            try {overdue.refreshReader(readerId);} catch(RuntimeException ex){
                // 每位读者单独事务，一人失败不阻止其他读者更新。
                log.error("刷新读者 {} 逾期状态失败",readerId,ex);
            }
        }
    }
}
