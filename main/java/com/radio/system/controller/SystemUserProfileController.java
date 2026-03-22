package com.radio.system.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * system 用户资料控制器
 *
 * 本次修复重点：
 * 1. 头像统一返回 public 路径，避免 PC 端 <img> 加载 /api 路径时被 401 拦截
 * 2. 兼容数据库中旧值 /api/system/file/avatar/**，查询时自动转成 /public/system/file/avatar/**
 * 3. 头像上传后直接保存 public 路径
 */
@RestController
@RequestMapping("/api/system/user")
public class SystemUserProfileController {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/profile")
    public Map<String, Object> getCurrentUserProfile(HttpServletRequest request) {
        String username = resolveCurrentUsername(request);

        String sql = "SELECT id, username, real_name, nick_name, avatar_url, phone, role_code, status " +
                "FROM sys_user WHERE username = ? LIMIT 1";

        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(sql, username);
        } catch (Exception e) {
            return buildResponse(500, "未找到当前用户资料", null);
        }

        String avatarUrl = normalizeAvatarUrlToPublic(value(row.get("avatar_url")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.get("id"));
        data.put("username", value(row.get("username")));
        data.put("realName", value(row.get("real_name")));
        data.put("nickName", value(row.get("nick_name")));
        data.put("avatarUrl", avatarUrl);
        data.put("avatar", avatarUrl); // 兼容 PC 端旧字段
        data.put("phone", value(row.get("phone")));
        data.put("roleCode", value(row.get("role_code")));
        data.put("roleName", roleName(value(row.get("role_code"))));
        data.put("status", row.get("status"));

        return buildResponse(200, "success", data);
    }

    @PutMapping("/profile/update")
    public Map<String, Object> updateCurrentUserProfile(
            HttpServletRequest request,
            @RequestBody UserProfileUpdateReq req
    ) {
        String username = resolveCurrentUsername(request);

        String nickName = trim(req.getNickName());
        String avatarUrl = normalizeAvatarUrlToPublic(trim(firstNonBlank(req.getAvatarUrl(), req.getAvatar())));

        if (!StringUtils.hasText(nickName)) {
            return buildResponse(500, "昵称不能为空", null);
        }

        String sql = "UPDATE sys_user " +
                "SET nick_name = ?, avatar_url = ?, update_time = ? " +
                "WHERE username = ?";

        int updated = jdbcTemplate.update(
                sql,
                nickName,
                avatarUrl,
                LocalDateTime.now(),
                username
        );

        if (updated <= 0) {
            return buildResponse(500, "资料保存失败", null);
        }

        return getCurrentUserProfile(request);
    }

    @PutMapping("/password/update")
    public Map<String, Object> updateCurrentUserPassword(
            HttpServletRequest request,
            @RequestBody PasswordUpdateReq req
    ) {
        String username = resolveCurrentUsername(request);

        if (!StringUtils.hasText(req.getOldPassword())) {
            return buildResponse(500, "原密码不能为空", null);
        }

        if (!StringUtils.hasText(req.getNewPassword())) {
            return buildResponse(500, "新密码不能为空", null);
        }

        if (req.getNewPassword().length() < 6) {
            return buildResponse(500, "新密码至少 6 位", null);
        }

        String querySql = "SELECT password FROM sys_user WHERE username = ? LIMIT 1";
        String dbPassword;
        try {
            dbPassword = jdbcTemplate.queryForObject(querySql, String.class, username);
        } catch (Exception e) {
            return buildResponse(500, "未找到当前用户", null);
        }

        // 当前毕业设计最小可运行方案：按明文比对
        if (!req.getOldPassword().equals(dbPassword)) {
            return buildResponse(500, "原密码不正确", null);
        }

        String updateSql = "UPDATE sys_user SET password = ?, update_time = ? WHERE username = ?";
        int updated = jdbcTemplate.update(
                updateSql,
                req.getNewPassword(),
                LocalDateTime.now(),
                username
        );

        if (updated <= 0) {
            return buildResponse(500, "密码修改失败", null);
        }

        return buildResponse(200, "密码修改成功", true);
    }

    @PostMapping("/avatar/upload")
    public Map<String, Object> uploadAvatar(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return buildResponse(500, "请选择要上传的图片", null);
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            return buildResponse(500, "头像图片不能超过 2MB", null);
        }

        String originalFilename = file.getOriginalFilename();
        String ext = getFileExt(originalFilename);

        if (!isAllowedImageExt(ext)) {
            return buildResponse(500, "仅支持 png/jpg/jpeg 图片", null);
        }

        String username = resolveCurrentUsername(request);
        String dateDir = java.time.LocalDate.now().toString().replace("-", "");
        String newFileName = username + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        String uploadRoot = Paths.get(System.getProperty("user.dir"), "uploads", "avatar").toString();
        File saveDir = Paths.get(uploadRoot, dateDir).toFile();
        if (!saveDir.exists() && !saveDir.mkdirs()) {
            return buildResponse(500, "头像目录创建失败", null);
        }

        File dest = new File(saveDir, newFileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            return buildResponse(500, "头像上传失败：" + e.getMessage(), null);
        }

        String relativePath = dateDir + "/" + newFileName;
        String avatarUrl = "/public/system/file/avatar/" + relativePath;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("avatarUrl", avatarUrl);
        data.put("avatar", avatarUrl); // 兼容旧字段
        data.put("fileName", newFileName);
        data.put("fileSize", file.getSize());

        return buildResponse(200, "头像上传成功", data);
    }

    private String resolveCurrentUsername(HttpServletRequest request) {
        String headerUsername = request.getHeader("X-Username");
        if (StringUtils.hasText(headerUsername)) {
            return headerUsername.trim();
        }
        return "admin";
    }

    private String normalizeAvatarUrlToPublic(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return "";
        }

        String result = avatarUrl.trim();

        // 兼容旧值：/api/system/file/avatar/**
        if (result.startsWith("/api/system/file/avatar/")) {
            result = result.replaceFirst("/api/system/file/avatar/", "/public/system/file/avatar/");
        }

        return result;
    }

    private boolean isAllowedImageExt(String ext) {
        return "png".equalsIgnoreCase(ext)
                || "jpg".equalsIgnoreCase(ext)
                || "jpeg".equalsIgnoreCase(ext);
    }

    private String getFileExt(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String value(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String v1, String v2) {
        if (StringUtils.hasText(v1)) {
            return v1.trim();
        }
        if (StringUtils.hasText(v2)) {
            return v2.trim();
        }
        return "";
    }

    private String roleName(String roleCode) {
        if ("ADMIN".equalsIgnoreCase(roleCode)) {
            return "管理员";
        }
        return roleCode;
    }

    private Map<String, Object> buildResponse(int code, String msg, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("msg", msg);
        result.put("data", data);
        return result;
    }

    @Data
    public static class UserProfileUpdateReq {
        private String nickName;
        private String avatarUrl;
        private String avatar;
    }

    @Data
    public static class PasswordUpdateReq {
        private String oldPassword;
        private String newPassword;
    }
}