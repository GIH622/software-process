package edu.course.library;

import edu.course.library.service.MutationProbe;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 所有测试固定北京时间 2026-10-20；每例独立清理数据，拒绝依赖运行当天日期。 */
@TestConfiguration
public class TestSupport {
    @Bean @Primary public Clock testClock(){
        return Clock.fixed(Instant.parse("2026-10-20T02:00:00Z"),ZoneId.of("Asia/Shanghai"));
    }
    @Bean @Primary public MutableProbe mutationProbe(){return new MutableProbe();}
    public static class MutableProbe implements MutationProbe {
        public final AtomicBoolean breakBorrow=new AtomicBoolean();
        public final AtomicBoolean breakReturn=new AtomicBoolean();
        @Override public void afterBorrowWrites(){
            if(breakBorrow.get())throw new IllegalStateException("测试专用：故意触发借阅回滚");
        }
        @Override public void afterReturnWrites(){
            if(breakReturn.get())throw new IllegalStateException("测试专用：故意触发归还回滚");
        }
        public void reset(){breakBorrow.set(false);breakReturn.set(false);}
    }
    public static void resetDatabase(JdbcTemplate jdbc,BCryptPasswordEncoder encoder){
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("DELETE FROM borrow_record");
        jdbc.update("DELETE FROM book_inventory");
        jdbc.update("DELETE FROM reader_account");
        for(int i=1;i<=3;i++)jdbc.update("INSERT INTO reader_account(card_no,name,password_hash,status,borrowed_count,borrow_limit,register_date) "+
            "VALUES(?,?,?,'NORMAL',0,?,?)",
            ""+(1000+i),"测试读者"+i,encoder.encode("Reader-Test#2026"),i==1?5:1,
            java.sql.Date.valueOf("2026-10-01"));
        jdbc.update("INSERT INTO book_inventory(barcode,isbn,title,author,category,location,status) "+
            "VALUES('BC-0001','9787536692930','三体','刘慈欣','科幻','A区','AVAILABLE')");
        jdbc.update("INSERT INTO book_inventory(barcode,isbn,title,author,category,location,status) "+
            "VALUES('BC-0002','9787536692930','三体','刘慈欣','科幻','B区','AVAILABLE')");
        jdbc.update("INSERT INTO book_inventory(barcode,isbn,title,author,category,location,status) "+
            "VALUES('BC-0003','9787559674311','格林童话','格林兄弟','儿童','儿童区','AVAILABLE')");
    }
}
