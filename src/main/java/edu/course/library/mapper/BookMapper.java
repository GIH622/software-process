package edu.course.library.mapper;

import edu.course.library.model.BookCopy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** book_inventory 每行代表一本有独立 barcode 的实体书。 */
@Repository
public class BookMapper {
    private final JdbcTemplate jdbc;
    public BookMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static BookCopy map(ResultSet rs,int n) throws java.sql.SQLException {
        return new BookCopy(rs.getLong("book_inventory_id"),rs.getString("barcode"),rs.getString("isbn"),
            rs.getString("title"),rs.getString("author"),rs.getString("category"),
            rs.getString("location"),rs.getString("status"));
    }
    public BookCopy findByBarcode(String barcode){
        List<BookCopy> rows=jdbc.query("SELECT * FROM book_inventory WHERE barcode=?",BookMapper::map,barcode);
        return rows.isEmpty()?null:rows.get(0);
    }
    /** 与 ReaderMapper 保持一致锁序：先读者，再此副本，再借阅记录。 */
    public BookCopy lockById(long id){
        List<BookCopy> rows=jdbc.query("SELECT * FROM book_inventory WHERE book_inventory_id=? FOR UPDATE",BookMapper::map,id);
        return rows.isEmpty()?null:rows.get(0);
    }
    public int markBorrowed(long id){return jdbc.update(
        "UPDATE book_inventory SET status='BORROWED' WHERE book_inventory_id=? AND status='AVAILABLE'",id);}
    public int markAvailable(long id){return jdbc.update(
        "UPDATE book_inventory SET status='AVAILABLE' WHERE book_inventory_id=? AND status='BORROWED'",id);}
    public int retire(String barcode){return jdbc.update(
        "UPDATE book_inventory SET status='RETIRED' WHERE barcode=? AND status='AVAILABLE'",barcode);}
    public long insert(String barcode,String isbn,String title,String author,String category,String location){
        GeneratedKeyHolder key=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement(
            "INSERT INTO book_inventory(barcode,isbn,title,author,category,location,status) "+
            "VALUES(?,?,?,?,?,?,'AVAILABLE')",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,barcode);ps.setString(2,isbn);ps.setString(3,title);
            ps.setString(4,author);ps.setString(5,category);ps.setString(6,location);return ps;},key);
        if(key.getKey()==null)throw new IllegalStateException("新增图书未返回主键");
        return key.getKey().longValue();
    }
    /** 书名、ISBN、作者检索；其中在馆库存数由同ISBN实体副本动态计算而非冗余库存字段。 */
    public List<Map<String,Object>> search(String keyword){
        String like="%"+keyword+"%";
        return jdbc.queryForList("SELECT b.barcode,b.isbn,b.title,b.author,b.category,b.location,b.status, "+
           "(SELECT COUNT(*) FROM book_inventory s WHERE s.isbn=b.isbn AND s.status='AVAILABLE') available_count "+
           "FROM book_inventory b WHERE b.title LIKE ? OR b.isbn LIKE ? OR b.author LIKE ? "+
           "ORDER BY b.title,b.barcode LIMIT 100",like,like,like);
    }
}
