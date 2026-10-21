package edu.course.library.service;

import edu.course.library.TestSupport;
import edu.course.library.exception.BusinessException;
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

/** R003 候选变更单独测试：开启配置后在H2中模拟已执行续借迁移的环境。 */
@SpringBootTest(properties="library.renewal-enabled=true")
@ActiveProfiles("test")
@Import(TestSupport.class)
class RenewalServiceIntegrationTest {
    @Autowired BorrowService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired TestSupport.MutableProbe probe;
    @BeforeEach void reset(){probe.reset();TestSupport.resetDatabase(jdbc,encoder);}
    private long borrow(){return service.borrow("1001","BC-0001","1001","READER").recordId();}
    @Test void cr001_successExtendsDueFifteenDaysAndIncrementsCounter(){
        long id=borrow();var receipt=service.renew(id,"1001","READER");
        assertEquals(LocalDate.of(2026,12,4),receipt.dueDate());
        assertEquals(1,jdbc.queryForObject("SELECT renew_count FROM borrow_record WHERE borrow_record_id=?",Integer.class,id));
    }
    @Test void cr001_secondRenewalIsRejected(){
        long id=borrow();service.renew(id,"1001","READER");
        assertEquals("RENEWAL_LIMIT",assertThrows(BusinessException.class,
            ()->service.renew(id,"1001","READER")).errorCode());
    }
    @Test void cr001_overdueRenewalIsRejected(){
        long id=borrow();jdbc.update("UPDATE borrow_record SET expected_return_date='2026-10-19' WHERE borrow_record_id=?",id);
        assertEquals("RENEWAL_OVERDUE",assertThrows(BusinessException.class,
            ()->service.renew(id,"1001","READER")).errorCode());
    }
    @Test void cr001_anotherReaderCannotRenew(){
        long id=borrow();assertEquals("NOT_YOUR_LOAN",assertThrows(BusinessException.class,
            ()->service.renew(id,"1002","READER")).errorCode());
    }
}
