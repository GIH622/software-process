package edu.course.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * QA（角色4）补充的 R003 续借接口用例，在开启续借开关的前提下，
 * 覆盖测试用例文档中仍未覆盖的两条分支：已归还记录不可续借、续借记录不存在。
 */
@SpringBootTest(properties = "library.renewal-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupport.class)
class QaRenewalSupplementTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired TestSupport.MutableProbe probe;

    private final ObjectMapper mapper = new ObjectMapper();

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

    private long borrowAndGetRecordId(String barcode, MockHttpSession session) throws Exception {
        String body = mvc.perform(post("/api/borrow").session(session)
                .header("X-CSRF-Token", session.getAttribute("CSRF_TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"" + barcode + "\",\"readerCardNo\":\"1001\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode node = mapper.readTree(body);
        return node.path("data").path("recordId").asLong();
    }

    // TC-R003-05 已归还的借阅记录不可续借
    @Test
    void tc112_returnedRecordCannotBeRenewed() throws Exception {
        MockHttpSession session = readerSession();
        long recordId = borrowAndGetRecordId("BC-0001", session);
        mvc.perform(post("/api/return").session(session)
                .header("X-CSRF-Token", session.getAttribute("CSRF_TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"barcode\":\"BC-0001\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/borrow_records/" + recordId + "/renew").session(session)
                .header("X-CSRF-Token", session.getAttribute("CSRF_TOKEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("RENEWAL_NOT_ACTIVE")));
    }

    // TC-R003-06 借阅记录不存在
    @Test
    void tc113_renewUnknownRecordReturnsNotFound() throws Exception {
        MockHttpSession session = readerSession();
        mvc.perform(post("/api/borrow_records/999999/renew").session(session)
                .header("X-CSRF-Token", session.getAttribute("CSRF_TOKEN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("LOAN_NOT_FOUND")));
    }
}
