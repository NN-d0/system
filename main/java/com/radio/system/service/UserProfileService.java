package com.radio.system.service;

import com.radio.system.dto.PasswordUpdateRequest;
import com.radio.system.dto.UserProfileUpdateRequest;
import com.radio.system.exception.BusinessException;
import com.radio.system.vo.UserProfileVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户个人设置服务
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public UserProfileVO getCurrentProfile(String authorizationHeader) {
        CurrentUser currentUser = resolveCurrentUser(authorizationHeader);

        String sql = """
                SELECT id, username, nick_name, avatar_url
                FROM sys_user
                WHERE id = ?
                LIMIT 1
                """;

        List<UserProfileVO> list = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            UserProfileVO vo = new UserProfileVO();
            vo.setId(rs.getLong("id"));
            vo.setUsername(rs.getString("username"));
            vo.setNickName(rs.getString("nick_name"));
            vo.setAvatarUrl(rs.getString("avatar_url"));
            return vo;
        }, currentUser.userId());

        if (list.isEmpty()) {
            throw new BusinessException(404, "用户不存在");
        }

        UserProfileVO vo = list.get(0);
        if (vo.getNickName() == null || vo.getNickName().isBlank()) {
            vo.setNickName(vo.getUsername());
        }
        return vo;
    }

    public void updateProfile(String authorizationHeader, UserProfileUpdateRequest request) {
        CurrentUser currentUser = resolveCurrentUser(authorizationHeader);

        Long count = jdbcTemplate.query("""
                        SELECT COUNT(1)
                        FROM sys_user
                        WHERE username = ? AND id <> ?
                        """,
                rs -> rs.next() ? rs.getLong(1) : 0L,
                request.getUsername(),
                currentUser.userId());

        if (count != null && count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        int rows = jdbcTemplate.update("""
                        UPDATE sys_user
                        SET username = ?, nick_name = ?, avatar_url = ?, update_time = ?
                        WHERE id = ?
                        """,
                request.getUsername(),
                request.getNickName(),
                request.getAvatarUrl(),
                LocalDateTime.now(),
                currentUser.userId());

        if (rows <= 0) {
            throw new BusinessException(404, "用户不存在");
        }
    }

    public void updatePassword(String authorizationHeader, PasswordUpdateRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(400, "两次输入的新密码不一致");
        }

        if (request.getNewPassword().length() < 6) {
            throw new BusinessException(400, "新密码长度不能少于6位");
        }

        CurrentUser currentUser = resolveCurrentUser(authorizationHeader);

        String currentPassword = jdbcTemplate.query("""
                        SELECT password
                        FROM sys_user
                        WHERE id = ?
                        LIMIT 1
                        """,
                rs -> rs.next() ? rs.getString("password") : null,
                currentUser.userId());

        if (currentPassword == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (!currentPassword.equals(request.getOldPassword())) {
            throw new BusinessException(400, "原密码不正确");
        }

        jdbcTemplate.update("""
                        UPDATE sys_user
                        SET password = ?, update_time = ?
                        WHERE id = ?
                        """,
                request.getNewPassword(),
                LocalDateTime.now(),
                currentUser.userId());
    }

    private CurrentUser resolveCurrentUser(String authorizationHeader) {
        String token = extractToken(authorizationHeader);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = tryGetLongClaim(claims, "userId");
        if (userId == null) {
            userId = tryGetLongClaim(claims, "id");
        }

        String subject = claims.getSubject();

        if (userId != null) {
            return new CurrentUser(userId, subject);
        }

        if (subject == null || subject.isBlank()) {
            throw new BusinessException(401, "无效的登录凭证");
        }

        Long dbUserId = jdbcTemplate.query("""
                        SELECT id
                        FROM sys_user
                        WHERE username = ?
                        LIMIT 1
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                subject);

        if (dbUserId == null) {
            throw new BusinessException(401, "登录用户不存在");
        }

        return new CurrentUser(dbUserId, subject);
    }

    private Long tryGetLongClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }

        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new BusinessException(401, "未提供登录凭证");
        }

        if (authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        return authorizationHeader;
    }

    private record CurrentUser(Long userId, String username) {
    }
}