package edu.course.library.service;

import edu.course.library.dto.Forms;
import edu.course.library.exception.BusinessException;
import edu.course.library.mapper.*;
import edu.course.library.model.Reader;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** R006：管理员维护读者和实体图书，所有写操作都是受权限保护的业务操作。 */
@Service
public class AdminService {
    private final ReaderMapper readers;private final BookMapper books;
    private final OverdueService overdue;private final BCryptPasswordEncoder encoder;
    private final OperationLogMapper logs;
    public AdminService(ReaderMapper readers,BookMapper books,OverdueService overdue,
                        BCryptPasswordEncoder encoder,OperationLogMapper logs){
        this.readers=readers;this.books=books;this.overdue=overdue;this.encoder=encoder;this.logs=logs;
    }
    @Transactional
    public long createReader(Forms.RegisterReader form,String operator){
        if(readers.findByCard(form.cardNo())!=null)
            throw new BusinessException(HttpStatus.CONFLICT,"READER_EXISTS","读者证号已注册");
        long id=readers.insert(form.cardNo(),form.name(),encoder.encode(form.password()),form.borrowLimit());
        logs.insert("REGISTER_READER",operator,id,null,"新增读者");return id;
    }
    @Transactional
    public long createBook(Forms.AddBook form,String operator){
        if(books.findByBarcode(form.barcode())!=null)
            throw new BusinessException(HttpStatus.CONFLICT,"BOOK_EXISTS","图书条码已存在");
        long id=books.insert(form.barcode(),form.isbn(),form.title(),form.author(),form.category(),form.location());
        logs.insert("ADD_BOOK",operator,null,form.isbn(),"图书入馆，barcode="+form.barcode());return id;
    }
    @Transactional
    public String setFreeze(long id,boolean frozen,String operator){
        Reader reader=readers.lockById(id);
        if(reader==null)throw new BusinessException(HttpStatus.NOT_FOUND,"READER_NOT_FOUND","读者不存在");
        if("INACTIVE".equals(reader.status()))throw new BusinessException(HttpStatus.CONFLICT,"READER_INACTIVE","已注销读者不可操作");
        readers.setFreeze(id,frozen);
        // 解冻并不等于允许逾期读者借书；立刻重算剩余未归还逾期。
        String status=frozen?"FROZEN":overdue.recomputeUnderLock(
            new Reader(reader.id(),reader.cardNo(),reader.name(),reader.passwordHash(),"NORMAL",
                       reader.borrowedCount(),reader.borrowLimit()));
        logs.insert(frozen?"FREEZE":"UNFREEZE",operator,id,null,"调整读者冻结状态（旧库无法区分冻结来源）");return status;
    }
    @Transactional
    public void deactivate(long id,String operator){
        Reader reader=readers.lockById(id);
        if(reader==null)throw new BusinessException(HttpStatus.NOT_FOUND,"READER_NOT_FOUND","读者不存在");
        if(reader.borrowedCount()>0)throw new BusinessException(HttpStatus.CONFLICT,"HAS_ACTIVE_LOANS","仍有未归还图书，不可注销");
        readers.deactivate(id);logs.insert("DEACTIVATE_READER",operator,id,null,"注销读者");
    }
    @Transactional
    public void retireBook(String barcode,String operator){
        if(books.retire(barcode)!=1)throw new BusinessException(HttpStatus.CONFLICT,"BOOK_NOT_AVAILABLE","不存在或已借出的图书不可下架");
        logs.insert("RETIRE_BOOK",operator,null,null,"馆藏下架 barcode="+barcode);
    }
}
