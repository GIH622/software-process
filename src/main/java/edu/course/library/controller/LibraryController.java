package edu.course.library.controller;

import edu.course.library.dto.ApiResult;
import edu.course.library.dto.Forms;
import edu.course.library.exception.BusinessException;
import edu.course.library.mapper.BookMapper;
import edu.course.library.mapper.LoanMapper;
import edu.course.library.service.BorrowService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 兼容 SRS 规定的 /api/borrow、/api/return、/api/books 等基本接口。 */
@RestController
public class LibraryController {
    private final BorrowService service;private final BookMapper books;private final LoanMapper loans;
    public LibraryController(BorrowService service,BookMapper books,LoanMapper loans){
        this.service=service;this.books=books;this.loans=loans;
    }
    @GetMapping("/api/health")
    public ApiResult<Map<String,String>> health(){
        // 这只是 HTTP 存活探针；数据库是否可用请调用已登录的业务接口或执行只读 SQL。
        return ApiResult.ok(Map.of("service","UP"));
    }
    @GetMapping("/api/books")
    public ApiResult<List<Map<String,Object>>> findBooks(@RequestParam(defaultValue="") String keyword){
        if(keyword.length()>100)throw new BusinessException(HttpStatus.BAD_REQUEST,"SEARCH_TOO_LONG","关键词不能超过100字符");
        return ApiResult.ok(books.search(keyword));
    }
    @PostMapping("/api/borrow")
    public ApiResult<Forms.Receipt> borrow(@Valid @RequestBody Forms.Borrow request,HttpSession session){
        return ApiResult.ok(service.borrow(request.readerCardNo(),request.barcode(),
            (String)session.getAttribute("ACCOUNT"),(String)session.getAttribute("ROLE")));
    }
    @PostMapping("/api/return")
    public ApiResult<Forms.Receipt> returnBook(@Valid @RequestBody Forms.Returned request,HttpSession session){
        return ApiResult.ok(service.returnBook(request.barcode(),
            (String)session.getAttribute("ACCOUNT"),(String)session.getAttribute("ROLE")));
    }
    /** R003：默认禁用；须先在 feature/CR-001 分支完成迁移、测试、评审，才能启用。 */
    @PostMapping("/api/borrow_records/{id}/renew")
    public ApiResult<Forms.Receipt> renew(@PathVariable long id,HttpSession session){
        return ApiResult.ok(service.renew(id,(String)session.getAttribute("ACCOUNT"),
            (String)session.getAttribute("ROLE")));
    }
    @GetMapping("/api/borrow_records/my")
    public ApiResult<List<Map<String,Object>>> myLoans(HttpSession session){
        if(!"READER".equals(session.getAttribute("ROLE")))
            throw new BusinessException(HttpStatus.FORBIDDEN,"READER_ONLY","此接口用于读者查看自己的借阅记录");
        return ApiResult.ok(loans.findMyLoans((Long)session.getAttribute("READER_ID")));
    }
}
