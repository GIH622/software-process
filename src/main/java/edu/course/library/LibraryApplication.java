package edu.course.library;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 应用入口。默认不自动修改数据库结构；启动前由成员3执行审核过的 SQL。
 * 开启定时任务，为 R005 每日更新仍未归还的逾期状态。
 */
@SpringBootApplication
@EnableScheduling
public class LibraryApplication {
    public static void main(String[] args) { SpringApplication.run(LibraryApplication.class, args); }

    /** 使用北京时间决定“今天是否逾期”，避免测试机器本地时区影响结果。 */
    @Bean public Clock libraryClock() { return Clock.system(ZoneId.of("Asia/Shanghai")); }

    /** 读者口令一律 BCrypt（成本因子10）；旧库 MD5 已随 2026-10-23 退役迁移清理。 */
    @Bean public BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }
}
