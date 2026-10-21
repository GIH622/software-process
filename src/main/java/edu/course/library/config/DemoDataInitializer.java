package edu.course.library.config;

import edu.course.library.mapper.BookMapper;
import edu.course.library.mapper.ReaderMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 仅 SPRING_PROFILES_ACTIVE=demo 才会插入样例数据；额外验证当前库名是 library_dev。
 * 绝不对队友的 library_db 自动插数据、删表或重置密码。
 */
@Profile("demo")
@Component
public class DemoDataInitializer implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(DemoDataInitializer.class);
    private final JdbcTemplate jdbc;private final ReaderMapper readers;private final BookMapper books;
    private final BCryptPasswordEncoder encoder;private final LibrarySettings settings;
    public DemoDataInitializer(JdbcTemplate jdbc,ReaderMapper readers,BookMapper books,
                               BCryptPasswordEncoder encoder,LibrarySettings settings){
        this.jdbc=jdbc;this.readers=readers;this.books=books;this.encoder=encoder;this.settings=settings;
    }
    @Override public void run(ApplicationArguments args){
        String db=jdbc.queryForObject("SELECT DATABASE()",String.class);
        if(!"library_dev".equals(db))throw new IllegalStateException(
            "安全保护：demo 模式只允许连接 library_dev，检测到的是 "+db);
        if(settings.demoReaderPassword.isBlank())throw new IllegalStateException(
            "demo 模式需要 LIBRARY_DEMO_READER_PASSWORD 环境变量（勿写入 GitHub）");
        for(int i=1;i<=10;i++){
            String card=""+(1000+i);
            if(readers.findByCard(card)==null)
                readers.insert(card,"演示读者"+i,encoder.encode(settings.demoReaderPassword),5);
        }
        // 与团队指南提到的条码保持一致；这些插入只针对新建开发库。
        String[][] stock={
            {"BC-0001","9787536692930","三体","刘慈欣","科幻","A区"},
            {"BC-0002","9787536692930","三体","刘慈欣","科幻","B区"},
            {"BC-0003","9787559674311","格林童话","格林兄弟","儿童","儿童区"},
            {"BC-CONC-001","9787111213826","并发测试专用书","测试数据","测试","测试区"}
        };
        for(String[] book:stock)if(books.findByBarcode(book[0])==null)
            books.insert(book[0],book[1],book[2],book[3],book[4],book[5]);
        log.info("demo 数据准备完成：仅 library_dev。读者账号1001~1010，样例条码BC-0001等");
    }
}
