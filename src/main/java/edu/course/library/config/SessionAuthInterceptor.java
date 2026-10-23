package edu.course.library.config;

import edu.course.library.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 课程本地演示版会话鉴权：
 * 1. 所有业务接口必须登录（401）；管理员接口检查 ADMIN 角色（403）。
 * 2. POST 等修改请求必须提交登录会话中的 X-CSRF-Token，防止跨站请求伪造。
 * 3. 配合 SameSite=Strict、HttpOnly Cookie，仅建议部署在可信本机教学环境。
 * 正式上线时应改用完整 Spring Security + HTTPS + 持久化管理员身份管理。
 */
@Component
public class SessionAuthInterceptor implements HandlerInterceptor {
    @Override public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String path = req.getRequestURI();
        // 登录创建会话和健康探针无需已有登录态。
        if (path.equals("/api/auth/login") || path.equals("/api/health")) return true;
        if (!path.startsWith("/api/")) return true;
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("ROLE") == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","请先登录");
        }
        if (path.startsWith("/api/admin/") && !"ADMIN".equals(session.getAttribute("ROLE"))) {
            throw new BusinessException(HttpStatus.FORBIDDEN,"ADMIN_REQUIRED","仅管理员可执行此操作");
        }
        if (!(req.getMethod().equals("GET") || req.getMethod().equals("HEAD"))) {
            String token = req.getHeader("X-CSRF-Token");
            if (token == null || !token.equals(session.getAttribute("CSRF_TOKEN"))) {
                throw new BusinessException(HttpStatus.FORBIDDEN,"INVALID_CSRF","会话安全令牌缺失或无效");
            }
        }
        return true;
    }
}
