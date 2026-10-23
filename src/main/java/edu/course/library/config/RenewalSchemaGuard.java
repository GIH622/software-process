package edu.course.library.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 可选 R003 的启动防护：防止在尚未运行数据库迁移时误打开续借开关。
 * 只查询当前连接库的字段、零行返回，不会修改数据；MySQL 与 H2 都支持该语法。
 */
@Component
public class RenewalSchemaGuard implements ApplicationRunner {
    private final LibrarySettings settings;
    private final JdbcTemplate jdbc;
    public RenewalSchemaGuard(LibrarySettings settings,JdbcTemplate jdbc){
        this.settings=settings;this.jdbc=jdbc;
    }
    @Override public void run(ApplicationArguments arguments){
        if(!settings.renewalEnabled)return;
        try {
            jdbc.queryForList("SELECT renew_count FROM borrow_record WHERE 1=0");
        } catch (org.springframework.dao.DataAccessException ex) {
            throw new IllegalStateException(
                "R003已开启但当前数据库缺少borrow_record.renew_count；请先完成备份和SQL迁移",ex);
        }
    }
}
