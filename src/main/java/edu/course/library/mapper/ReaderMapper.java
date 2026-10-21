package edu.course.library.mapper;

import edu.course.library.model.Reader;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** reader_account 的 SQL 只放在此类。使用 ? 参数绑定防 SQL 注入。 */
@Repository
public class ReaderMapper {
    private final JdbcTemplate jdbc;
    public ReaderMapper(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    private static Reader map(ResultSet rs, int row) throws java.sql.SQLException {
        return new Reader(rs.getLong("reader_id"),rs.getString("card_no"),rs.getString("name"),
            rs.getString("password_md5"),rs.getString("status"),
            rs.getInt("borrowed_count"),rs.getInt("borrow_limit"));
    }
    public Reader findByCard(String cardNo) {
        List<Reader> rows=jdbc.query("SELECT * FROM reader_account WHERE card_no=?",ReaderMapper::map,cardNo);
        return rows.isEmpty()?null:rows.get(0);
    }
    /** FOR UPDATE 持有读者行锁直到事务结束；确保同一读者借阅上限的检查串行执行。 */
    public Reader lockByCard(String cardNo) {
        List<Reader> rows=jdbc.query("SELECT * FROM reader_account WHERE card_no=? FOR UPDATE",ReaderMapper::map,cardNo);
        return rows.isEmpty()?null:rows.get(0);
    }
    public Reader lockById(long id) {
        List<Reader> rows=jdbc.query("SELECT * FROM reader_account WHERE reader_id=? FOR UPDATE",ReaderMapper::map,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public int changeBorrowedCount(long id,int delta) {
        // 条件 WHERE 防止因未来代码回归导致计数为负数或突破上限。
        return jdbc.update("UPDATE reader_account SET borrowed_count=borrowed_count+? "+
            "WHERE reader_id=? AND borrowed_count+? >=0 AND borrowed_count+? <= borrow_limit",
            delta,id,delta,delta);
    }
    public int setComputedStatus(long id,String status) {
        return jdbc.update("UPDATE reader_account SET status=? WHERE reader_id=? AND status<>'INACTIVE'",status,id);
    }
    /** 既有库没有 manual_frozen 标记，FROZEN 为持续冻结；解冻后重算剩余逾期。 */
    public int setFreeze(long id,boolean frozen) {
        return jdbc.update("UPDATE reader_account SET status=? WHERE reader_id=? AND status<>'INACTIVE'",
            frozen?"FROZEN":"NORMAL",id);
    }
    /** 遗留的 MD5 在验证通过后原位升级为 BCrypt（长度60，原 varchar(64) 可容纳）。 */
    public int upgradeLegacyPassword(long id,String oldHash,String newHash) {
        return jdbc.update("UPDATE reader_account SET password_md5=? WHERE reader_id=? AND password_md5=?",
            newHash,id,oldHash);
    }
    public int deactivate(long id) {return jdbc.update("UPDATE reader_account SET status='INACTIVE' WHERE reader_id=?",id);}
    public long insert(String cardNo,String name,String passwordHash,int limit) {
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement(
            "INSERT INTO reader_account(card_no,name,password_md5,status,borrowed_count,borrow_limit,register_date) "+
            "VALUES(?,?,?,'NORMAL',0,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,cardNo);ps.setString(2,name);ps.setString(3,passwordHash);ps.setInt(4,limit);
            ps.setDate(5,java.sql.Date.valueOf(LocalDate.now()));return ps;},key);
        if(key.getKey()==null)throw new IllegalStateException("新增读者未返回主键");
        return key.getKey().longValue();
    }
    public List<Long> findIds() {
        return jdbc.query("SELECT reader_id FROM reader_account WHERE status<>'INACTIVE' ORDER BY reader_id",
            (rs,n)->rs.getLong(1));
    }
}
