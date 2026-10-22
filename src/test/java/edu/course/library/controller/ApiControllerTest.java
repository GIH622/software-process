package edu.course.library.controller;

import edu.course.library.TestSupport;
import jakarta.servlet.http.HttpSession;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP 测试：登录、会话、CSRF、权限、基本 REST API，避免只测 Service 忽略接口安全。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupport.class)
class ApiControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BCryptPasswordEncoder encoder;
    @Autowired TestSupport.MutableProbe probe;
    @BeforeEach void reset(){probe.reset();TestSupport.resetDatabase(jdbc,encoder);}
    private MockHttpSession readerSession() throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
             .content("{\"account\":\"1001\",\"password\":\"Reader-Test#2026\"}"))
             .andExpect(status().isOk()).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private MockHttpSession adminSession() throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
             .content("{\"account\":\"testadmin\",\"password\":\"TestAdmin-Only#2026\"}"))
             .andExpect(status().isOk()).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    @Test void tc019_healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.data.service").value("UP"));
    }
    @Test void tc020_businessApiRequiresLogin() throws Exception {
        mvc.perform(get("/api/books")).andExpect(status().isUnauthorized());
    }
    @Test void tc021_invalidLoginRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"account\":\"1001\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void tc022_searchWorksAfterLogin() throws Exception {
        mvc.perform(get("/api/books").param("keyword","三体").session(readerSession()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }
    @Test void tc023_borrowWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/api/borrow").session(readerSession()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"barcode\":\"BC-0001\",\"readerCardNo\":\"1001\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void tc024_borrowWithValidCsrfSucceeds() throws Exception {
        MockHttpSession session=readerSession();
        mvc.perform(post("/api/borrow").session(session)
            .header("X-CSRF-Token",session.getAttribute("CSRF_TOKEN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"barcode\":\"BC-0001\",\"readerCardNo\":\"1001\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("BORROWED"));
    }
    @Test void tc025_readerCannotAccessAdminApi() throws Exception {
        mvc.perform(get("/api/admin/logs").session(readerSession())).andExpect(status().isForbidden());
    }
    @Test void tc026_adminCanListLogs() throws Exception {
        mvc.perform(get("/api/admin/logs").session(adminSession())).andExpect(status().isOk());
    }
    @Test void tc027_badBorrowBodyRejected() throws Exception {
        MockHttpSession session=readerSession();
        mvc.perform(post("/api/borrow").session(session).header("X-CSRF-Token",session.getAttribute("CSRF_TOKEN"))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }
    @Test void tc030_adminPostRequiresCsrf() throws Exception {
        mvc.perform(post("/api/admin/readers/1/freeze").session(adminSession()))
            .andExpect(status().isForbidden());
    }
    @Test void tc028_renewalIsDisabledByDefault() throws Exception {
        MockHttpSession session=readerSession();
        mvc.perform(post("/api/borrow_records/1/renew").session(session)
            .header("X-CSRF-Token",session.getAttribute("CSRF_TOKEN")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("RENEWAL_DISABLED")));
    }
}
