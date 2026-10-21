package edu.course.library.controller;

import edu.course.library.config.LibrarySettings;
import edu.course.library.dto.ApiResult;
import edu.course.library.dto.Forms;
import edu.course.library.exception.BusinessException;
import edu.course.library.mapper.ReaderMapper;
import edu.course.library.model.Reader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 简单 Session 登录：管理员密码只从环境变量读取；兼容旧库MD5并支持按需升级BCrypt。
 * 绝不在响应、日志或 GitHub 中暴露密码。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final ReaderMapper readers;
    private final BCryptPasswordEncoder encoder;
    private final LibrarySettings config;
    private final SecureRandom random=new SecureRandom();
    public AuthController(ReaderMapper readers,BCryptPasswordEncoder encoder,LibrarySettings config){
        this.readers=readers;this.encoder=encoder;this.config=config;
    }
    @PostMapping("/login")
    public ApiResult<Forms.LoginInfo> login(@Valid @RequestBody Forms.Login form,HttpServletRequest request){
        String role;String display;Long readerId=null;
        if(form.account().equals(config.adminAccount) && !config.adminPassword.isBlank()
            && MessageDigest.isEqual(form.password().getBytes(StandardCharsets.UTF_8),
                config.adminPassword.getBytes(StandardCharsets.UTF_8))) {
            role="ADMIN";display="管理员";
        } else {
            Reader reader=readers.findByCard(form.account());
            if(reader==null || "INACTIVE".equals(reader.status()))
                throw new BusinessException(HttpStatus.UNAUTHORIZED,"LOGIN_FAILED","账号或密码错误");
            String stored=reader.passwordHash();
            boolean bcrypt=stored!=null && stored.startsWith("$2");
            boolean valid=bcrypt?encoder.matches(form.password(),stored):
                (config.legacyMd5LoginEnabled && matchesLegacyMd5(form.password(),stored));
            if(!valid)throw new BusinessException(HttpStatus.UNAUTHORIZED,"LOGIN_FAILED","账号或密码错误");
            // 旧库样例为MD5；仅显式开启升级开关才会登录后原位升级BCrypt，默认不改动共享库密码。
            // 原数据库字段名仍为password_md5（长度64）；BCrypt实际长度60，无须破坏性迁移。
            if(!bcrypt && config.legacyMd5UpgradeEnabled)
                readers.upgradeLegacyPassword(reader.id(),stored,encoder.encode(form.password()));
            role="READER";display=reader.name();readerId=reader.id();
        }
        // 旧会话失效后再创建新会话，降低会话固定风险。
        HttpSession previous=request.getSession(false);
        if(previous!=null)previous.invalidate();
        HttpSession session=request.getSession(true);
        byte[] secret=new byte[32];random.nextBytes(secret);
        String csrf=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        session.setAttribute("ROLE",role);session.setAttribute("ACCOUNT",form.account());
        session.setAttribute("NAME",display);session.setAttribute("CSRF_TOKEN",csrf);
        if(readerId!=null)session.setAttribute("READER_ID",readerId);
        return ApiResult.ok(new Forms.LoginInfo(role,form.account(),display,csrf));
    }
    /** 仅用于迁移历史数据库：不记录、回显或保存任何原始密码。 */
    private static boolean matchesLegacyMd5(String raw,String hash){
        if(hash==null || !hash.matches("(?i)[0-9a-f]{32}"))return false;
        try {
            byte[] bytes=MessageDigest.getInstance("MD5").digest(raw.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(bytes,HexFormat.of().parseHex(hash));
        } catch(NoSuchAlgorithmException ex){ throw new IllegalStateException("JDK缺失MD5支持",ex); }
    }
    @GetMapping("/me")
    public ApiResult<Forms.LoginInfo> me(HttpSession session){
        return ApiResult.ok(new Forms.LoginInfo(
            (String)session.getAttribute("ROLE"),(String)session.getAttribute("ACCOUNT"),
            (String)session.getAttribute("NAME"),(String)session.getAttribute("CSRF_TOKEN")));
    }
    @PostMapping("/logout")
    public ApiResult<String> logout(HttpSession session){session.invalidate();return ApiResult.ok("已退出登录");}
}
