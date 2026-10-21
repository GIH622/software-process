package edu.course.library.mapper;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 成功借还的数据库操作日志跟随业务事务提交；若中途回滚则操作日志也应回滚。
 * 失败日志由 ApiExceptionHandler 写入 logs/library.log，避免在回滚事务内存失败日志。
 */
@Repository
public class OperationLogMapper {
    private final JdbcTemplate jdbc;
    public OperationLogMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void insert(String action,String operator,Long readerId,String isbn,String detail){
        jdbc.update("INSERT INTO operation_log(operation,operator,reader_id,book_isbn,detail) VALUES(?,?,?,?,?)",
            action,operator,readerId,isbn,detail);
    }
    public List<Map<String,Object>> latest(){return jdbc.queryForList(
        "SELECT log_id,operation,operator,reader_id,book_isbn,detail,op_time "+
        "FROM operation_log ORDER BY log_id DESC LIMIT 100");}
}
