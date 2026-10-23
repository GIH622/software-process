package edu.course.library.controller;

import edu.course.library.dto.ApiResult;
import edu.course.library.dto.Forms;
import edu.course.library.mapper.LoanMapper;
import edu.course.library.mapper.OperationLogMapper;
import edu.course.library.mapper.ReaderMapper;
import edu.course.library.service.AdminService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 管理员 API 在拦截器里先校验 ADMIN 身份和修改操作的 CSRF 令牌。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;private final LoanMapper loans;
    private final OperationLogMapper logs;private final ReaderMapper readers;private final Clock clock;
    public AdminController(AdminService admin,LoanMapper loans,OperationLogMapper logs,
                           ReaderMapper readers,Clock clock){
        this.admin=admin;this.loans=loans;this.logs=logs;this.readers=readers;this.clock=clock;
    }
    @PostMapping("/readers")
    public ApiResult<Map<String,Long>> register(@Valid @RequestBody Forms.RegisterReader req,HttpSession s){
        return ApiResult.ok(Map.of("readerId",admin.createReader(req,(String)s.getAttribute("ACCOUNT"))));
    }
    @PostMapping("/books")
    public ApiResult<Map<String,Long>> addBook(@Valid @RequestBody Forms.AddBook req,HttpSession s){
        return ApiResult.ok(Map.of("bookId",admin.createBook(req,(String)s.getAttribute("ACCOUNT"))));
    }
    @PostMapping("/readers/{id}/freeze")
    public ApiResult<String> freeze(@PathVariable long id,HttpSession s){
        return ApiResult.ok(admin.setFreeze(id,true,(String)s.getAttribute("ACCOUNT")));
    }
    @PostMapping("/readers/{id}/unfreeze")
    public ApiResult<String> unfreeze(@PathVariable long id,HttpSession s){
        return ApiResult.ok(admin.setFreeze(id,false,(String)s.getAttribute("ACCOUNT")));
    }
    @PostMapping("/readers/{id}/deactivate")
    public ApiResult<String> deactivate(@PathVariable long id,HttpSession s){
        admin.deactivate(id,(String)s.getAttribute("ACCOUNT"));return ApiResult.ok("已注销");
    }
    @PostMapping("/books/{barcode}/retire")
    public ApiResult<String> retire(@PathVariable String barcode,HttpSession s){
        admin.retireBook(barcode,(String)s.getAttribute("ACCOUNT"));return ApiResult.ok("已下架");
    }
    @GetMapping("/readers")
    public ApiResult<List<Map<String,Object>>> readers(){return ApiResult.ok(readers.findAllForAdmin());}
    @GetMapping("/overdue")
    public ApiResult<List<Map<String,Object>>> overdue(){return ApiResult.ok(loans.overdueRows(LocalDate.now(clock)));}
    @GetMapping("/logs")
    public ApiResult<List<Map<String,Object>>> logs(){return ApiResult.ok(logs.latest());}
    /** R006 配套：CSV UTF-8 BOM 可由 Excel 打开；阻止公式注入，避免导出后触发恶意公式。 */
    @GetMapping(value="/borrow-records/export",produces="text/csv")
    public ResponseEntity<byte[]> csv(){
        StringBuilder text=new StringBuilder("\uFEFF");
        text.append("recordId,readerCardNo,barcode,title,borrowTime,dueDate,returnTime,overdueDays,status\r\n");
        for(Map<String,Object> row:loans.exportRows()){
            for(String key:new String[]{"borrow_record_id","card_no","barcode","title","borrow_time",
                                       "expected_return_date","actual_return_time","overdue_days","status"}){
                if(!key.equals("borrow_record_id")) text.append(',');
                text.append(csvCell(row.get(key)));
            }
            text.append("\r\n");
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=borrow-records.csv")
            .contentType(new MediaType("text","csv",StandardCharsets.UTF_8))
            .body(text.toString().getBytes(StandardCharsets.UTF_8));
    }
    static String csvCell(Object value){
        String s=value==null?"":value.toString();
        if(!s.isEmpty()&&"=+-@".indexOf(s.charAt(0))>=0)s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
}
