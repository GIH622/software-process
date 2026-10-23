package edu.course.library.controller;

import edu.course.library.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * QA（角色4）补充的接口用例，专门覆盖原自动化测试未涉及的分支：
 * 读者越权借阅、无效归还、检索边界、管理类接口的冲突校验、CSV 导出安全、逾期列表。
 * 对应《测试用例文档》中标记为“未覆盖”的条目，见各方法注释里的用例编号。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupport.class)
class QaSupplementApiTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired TestSupport.MutableProbe probe;

    @BeforeEach
    void reset() {
        probe.reset();
        TestSupport.resetDatabase(jdbc, encoder);
    }

    private MockHttpSession readerSession() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"1001\",\"password\":\"Reader-Test#2026\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession adminSession() throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"testadmin\",\"password\":\"TestAdmin-Only#2026\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long readerId(String cardNo) {
        Long id = jdbc.queryForObject("SELECT reader_id FROM reader_account WHERE card_no=?", Long.class, cardNo);
        return id == null ? -1 : id;
    }

    private void borrowOk(String barcode, String cardNo, MockHttpSession session) throws Exception {
        mvc.perform(post("/api/borrow").session(session)
                .header("X-CSRF-Token", session.getAttribute("CSRF_TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"" + barcode + "\",\"readerCardNo\":\"" + cardNo + "\"}"))
                .andExpect(status().isOk());
    }

    // TC-R001-08 读者越权替他人借书
    @Test
    void tc101_readerCannotBorrowForAnotherReader() throws Exception {
        MockHttpSession session = readerSession();
        mvc.perform(post("/api/borrow").session(session)
                .header("X-CSRF-Token", session.getAttribute("CSRF_TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"BC-0001\",\"readerCardNo\":\"1002\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("NOT_YOUR_LOAN")));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_record WHERE status='BORROWED'", Integer.class);
        assertEquals(0, count, "越权借阅不应产生任何借阅记录");
    }

    // TC-R002-04 归还从未借出的图书
    @Test
    void tc102_returnBookWithoutActiveLoanRejected() throws Exception {
        MockHttpSession session = readerSession();
        mvc.perform(post("/api/return").session(session)
                .header("X-CSRF-Token", session.getAttribute("CSRF_TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"BC-0003\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("NOT_BORROWED")));
    }

    // TC-R004-02 检索关键词超过长度上限
    @Test
    void tc103_searchKeywordTooLongRejected() throws Exception {
        mvc.perform(get("/api/books").param("keyword", "a".repeat(101)).session(readerSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("SEARCH_TOO_LONG")));
    }

    // TC-R004-03 无匹配关键词返回空数组而不是错误
    @Test
    void tc104_searchWithoutMatchReturnsEmptyArray() throws Exception {
        mvc.perform(get("/api/books").param("keyword", "ZZZ绝不存在的书名ZZZ").session(readerSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // TC-R005-01 管理员查询逾期列表，且只包含未归还的逾期记录
    @Test
    void tc105_adminOverdueListContainsOverdueLoanOnly() throws Exception {
        MockHttpSession reader = readerSession();
        borrowOk("BC-0001", "1001", reader);
        jdbc.update("UPDATE borrow_record SET expected_return_date=? WHERE status='BORROWED'",
                java.sql.Date.valueOf("2026-10-15")); // 测试时钟固定 2026-10-20，逾期 5 天
        mvc.perform(get("/api/admin/overdue").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // TC-R006-01 新增读者：证号重复被拒
    @Test
    void tc106_adminCreateDuplicateReaderRejected() throws Exception {
        MockHttpSession admin = adminSession();
        mvc.perform(post("/api/admin/readers").session(admin)
                .header("X-CSRF-Token", admin.getAttribute("CSRF_TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardNo\":\"1001\",\"name\":\"重复读者\",\"password\":\"Passw0rd#2026\",\"borrowLimit\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("READER_EXISTS")));
    }

    // TC-R006-02 新增图书：条码重复被拒
    @Test
    void tc107_adminCreateDuplicateBookRejected() throws Exception {
        MockHttpSession admin = adminSession();
        mvc.perform(post("/api/admin/books").session(admin)
                .header("X-CSRF-Token", admin.getAttribute("CSRF_TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"BC-0001\",\"isbn\":\"9787536692930\",\"title\":\"重复图书\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("BOOK_EXISTS")));
    }

    // TC-R006-03 注销仍有未还图书的读者
    @Test
    void tc108_deactivateReaderWithActiveLoanRejected() throws Exception {
        borrowOk("BC-0001", "1001", readerSession());
        MockHttpSession admin = adminSession();
        mvc.perform(post("/api/admin/readers/" + readerId("1001") + "/deactivate").session(admin)
                .header("X-CSRF-Token", admin.getAttribute("CSRF_TOKEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("HAS_ACTIVE_LOANS")));
        String status = jdbc.queryForObject(
                "SELECT status FROM reader_account WHERE card_no='1001'", String.class);
        assertEquals("NORMAL", status, "注销失败后读者状态不应变化");
    }

    // TC-R006-04 下架已借出的图书
    @Test
    void tc109_retireBorrowedBookRejected() throws Exception {
        borrowOk("BC-0001", "1001", readerSession());
        MockHttpSession admin = adminSession();
        mvc.perform(post("/api/admin/books/BC-0001/retire").session(admin)
                .header("X-CSRF-Token", admin.getAttribute("CSRF_TOKEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("BOOK_NOT_AVAILABLE")));
    }

    // TC-R006-09 管理员访问读者专属接口被拒
    @Test
    void tc110_adminCannotUseReaderOnlyEndpoint() throws Exception {
        mvc.perform(get("/api/borrow_records/my").session(adminSession()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("READER_ONLY")));
    }

    // TC-R006-06 借阅记录导出：UTF-8 BOM + 公式注入防护
    @Test
    void tc111_csvExportHasBomAndSanitizesFormulaInjection() throws Exception {
        borrowOk("BC-0003", "1001", readerSession());
        jdbc.update("UPDATE book_inventory SET title=? WHERE barcode='BC-0003'", "=1+1");
        byte[] body = mvc.perform(get("/api/admin/borrow-records/export").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(body.length > 3, "导出内容不应为空");
        assertEquals((byte) 0xEF, body[0], "应以 UTF-8 BOM 开头");
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);
        String text = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("'=1+1"), "以 = 开头的字段必须被前置单引号转义，防止 Excel 公式注入");
    }

    // TC-R007-02 失败的操作不应留下成功的业务日志
    @Test
    void tc114_failedBorrowDoesNotWriteSuccessLog() throws Exception {
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"1002\",\"password\":\"Reader-Test#2026\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        borrowOk("BC-0001", "1002", session); // 测试读者 1002 的借阅上限为 1
        mvc.perform(post("/api/borrow").session(session)
                .header("X-CSRF-Token", session.getAttribute("CSRF_TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"BC-0002\",\"readerCardNo\":\"1002\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("BORROW_LIMIT")));
        Integer logs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation='BORROW'", Integer.class);
        assertEquals(1, logs, "被拒绝的借阅不应写入 BORROW 成功日志");
    }
}
