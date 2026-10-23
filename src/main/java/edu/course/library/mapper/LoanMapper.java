package edu.course.library.mapper;

import edu.course.library.model.Loan;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * borrow_record SQL。团队现有库目前没有活动借阅唯一索引；API 通过读者→副本锁防并发。
 * 部署前建议评审并运行 sql/03_add_active_loan_unique_index.sql 增强数据库层保证。
 */
@Repository
public class LoanMapper {
    private final JdbcTemplate jdbc;
    public LoanMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static Loan map(ResultSet rs,int n) throws java.sql.SQLException {
        var returned=rs.getTimestamp("actual_return_time");
        return new Loan(rs.getLong("borrow_record_id"),rs.getLong("reader_id"),
            rs.getLong("book_inventory_id"),rs.getTimestamp("borrow_time").toLocalDateTime(),
            rs.getDate("expected_return_date").toLocalDate(),
            returned==null?null:returned.toLocalDateTime(),rs.getInt("overdue_days"),rs.getString("status"));
    }
    /** R003 变更候选：仅当续借迁移已执行且功能开关开启才会查询此列。 */
    public int renewalCount(long recordId){
        Integer n=jdbc.queryForObject("SELECT renew_count FROM borrow_record WHERE borrow_record_id=?",Integer.class,recordId);
        if(n==null)throw new IllegalStateException("续借记录不存在");
        return n;
    }
    /** 乐观WHERE保护并发续借，返回影响行数；调用方会先锁定读者→副本→借阅行。 */
    public int extendDueDate(long recordId,int maxRenewals,LocalDate today,LocalDate newDue){
        return jdbc.update("UPDATE borrow_record SET expected_return_date=?, renew_count=renew_count+1 "+
            "WHERE borrow_record_id=? AND status='BORROWED' AND renew_count<? AND expected_return_date>=?",
            java.sql.Date.valueOf(newDue),recordId,maxRenewals,java.sql.Date.valueOf(today));
    }
    public Loan findById(long id){
        List<Loan> rows=jdbc.query("SELECT * FROM borrow_record WHERE borrow_record_id=?",LoanMapper::map,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public Loan lockById(long id){
        List<Loan> rows=jdbc.query("SELECT * FROM borrow_record WHERE borrow_record_id=? FOR UPDATE",LoanMapper::map,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public Loan findActiveByBookId(long bookId){
        List<Loan> rows=jdbc.query("SELECT * FROM borrow_record WHERE book_inventory_id=? AND status='BORROWED'",
            LoanMapper::map,bookId);
        return rows.isEmpty()?null:rows.get(0);
    }
    public boolean hasBorrowedSameIsbnToday(long readerId,String isbn,LocalDate today){
        // 同一读者同一本ISBN同一天不能重复登记（BR-03）。即使已经还书也不放行。
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM borrow_record r JOIN book_inventory b "+
            "ON b.book_inventory_id=r.book_inventory_id WHERE r.reader_id=? AND b.isbn=? "+
            "AND r.borrow_time>=? AND r.borrow_time<?",Integer.class,readerId,isbn,
            java.sql.Timestamp.valueOf(today.atStartOfDay()),
            java.sql.Timestamp.valueOf(today.plusDays(1).atStartOfDay()));
        return count!=null && count>0;
    }
    public long insert(long readerId,long bookId,LocalDateTime now,LocalDate due){
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement(
          "INSERT INTO borrow_record(reader_id,book_inventory_id,borrow_time,expected_return_date,status) "+
          "VALUES(?,?,?,?,'BORROWED')",Statement.RETURN_GENERATED_KEYS);
          ps.setLong(1,readerId);ps.setLong(2,bookId);
          ps.setTimestamp(3,java.sql.Timestamp.valueOf(now));ps.setDate(4,java.sql.Date.valueOf(due));return ps;},key);
        if(key.getKey()==null)throw new IllegalStateException("插入借阅记录时主键未产生");
        return key.getKey().longValue();
    }
    public int markReturned(long id,LocalDateTime now,int overdueDays){
        return jdbc.update("UPDATE borrow_record SET status='RETURNED',actual_return_time=?,overdue_days=? "+
            "WHERE borrow_record_id=? AND status='BORROWED'",now,overdueDays,id);
    }
    /** 找最早未归还的应还日期，日期越早说明逾期越严重。 */
    public int currentMaxOverdueDays(long readerId,LocalDate today){
        LocalDate earliest=jdbc.queryForObject(
            "SELECT MIN(expected_return_date) FROM borrow_record WHERE reader_id=? AND status='BORROWED'",
            (rs,n)->rs.getDate(1)==null?null:rs.getDate(1).toLocalDate(),readerId);
        return earliest==null?0:(int)Math.max(0,ChronoUnit.DAYS.between(earliest,today));
    }
    public List<Map<String,Object>> findMyLoans(long readerId){
        return jdbc.queryForList("SELECT r.borrow_record_id,r.status,r.borrow_time,r.expected_return_date, "+
            "r.actual_return_time,r.overdue_days,b.barcode,b.title "+
            "FROM borrow_record r JOIN book_inventory b ON r.book_inventory_id=b.book_inventory_id "+
            "WHERE r.reader_id=? ORDER BY r.borrow_record_id DESC LIMIT 100",readerId);
    }
    public List<Map<String,Object>> overdueRows(LocalDate today){
        return jdbc.queryForList("SELECT a.card_no,a.name,b.barcode,b.title,r.expected_return_date "+
            "FROM borrow_record r JOIN reader_account a ON r.reader_id=a.reader_id "+
            "JOIN book_inventory b ON r.book_inventory_id=b.book_inventory_id "+
            "WHERE r.status='BORROWED' AND r.expected_return_date<? "+
            "ORDER BY r.expected_return_date LIMIT 100",today);
    }
    public List<Map<String,Object>> exportRows(){
        return jdbc.queryForList("SELECT r.borrow_record_id,a.card_no,b.barcode,b.title,r.borrow_time, "+
            "r.expected_return_date,r.actual_return_time,r.overdue_days,r.status "+
            "FROM borrow_record r JOIN reader_account a ON r.reader_id=a.reader_id "+
            "JOIN book_inventory b ON r.book_inventory_id=b.book_inventory_id "+
            "ORDER BY r.borrow_record_id LIMIT 10000");
    }
}
