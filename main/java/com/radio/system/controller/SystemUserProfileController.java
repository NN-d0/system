package com.radio.system.controller;

import com.radio.system.common.ApiResponse;
import com.radio.system.context.UserContext;
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
 * 当前版本统一口径：
 * 1. 资料接口统一按当前登录用户处理
 * 2. 不再通过 X-Username 猜测用户
 * 3. 不再返回旧字段 avatar
 * 4. 资料更新只允许修改 nickName、avatarUrl
 * 5. 头像上传统一返回 avatarUrl
 */
@RestController
@RequestMapping("/api/system/user")
public class SystemUserProfileController {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 查询当前登录用户资料
     */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getCurrentUserProfile() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未登录或登录已失效");
        }

        String sql = "SELECT id, username, real_name, nick_name, avatar_url, phone, role_code, status " +
                "FROM sys_user WHERE id = ? LIMIT 1";

        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(sql, userId);
        } catch (Exception e) {
            return ApiResponse.fail(500, "未找到当前用户资料");
        }

        String avatarUrl = normalizeAvatarUrlToPublic(value(row.get("avatar_url")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.get("id"));
        data.put("username", value(row.get("username")));
        data.put("realName", value(row.get("real_name")));
        data.put("nickName", value(row.get("nick_name")));
        data.put("avatarUrl", avatarUrl);
        data.put("phone", value(row.get("phone")));
        data.put("roleCode", value(row.get("role_code")));
        data.put("roleName", roleName(value(row.get("role_code"))));
        data.put("status", row.get("status"));

        return ApiResponse.success("success", data);
    }

    /**
     * 更新当前登录用户资料
     * 仅允许修改：nickName、avatarUrl
     */
    @PutMapping("/profile/update")
    public ApiResponse<Map<String, Object>> updateCurrentUserProfile(@RequestBody UserProfileUpdateReq req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未登录或登录已失效");
        }

        if (req == null) {
            return ApiResponse.fail(500, "请求参数不能为空");
        }

        String nickName = trim(req.getNickName());
        String avatarUrl = normalizeAvatarUrlToPublic(trim(req.getAvatarUrl()));

        if (!StringUtils.hasText(nickName)) {
            return ApiResponse.fail(500, "昵称不能为空");
        }

        String sql = "UPDATE sys_user " +
                "SET nick_name = ?, avatar_url = ?, update_time = ? " +
                "WHERE id = ?";

        int updated = jdbcTemplate.update(
                sql,
                nickName,
                avatarUrl,
                LocalDateTime.now(),
                userId
        );

        if (updated <= 0) {
            return ApiResponse.fail(500, "资料保存失败");
        }

        return getCurrentUserProfile();
    }

    /**
     * 修改当前登录用户密码
     *
     * 当前毕业设计最小可运行方案：
     * 仍按明文密码比对
     */
    @PutMapping("/password/update")
    public ApiResponse<Boolean> updateCurrentUserPassword(@RequestBody PasswordUpdateReq req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return ApiResponse.fail(401, "未登录或登录已失效");
        }

        if (req == null) {
            return ApiResponse.fail(500, "请求参数不能为空");
        }

        if (!StringUtils.hasText(req.getOldPassword())) {
            return ApiResponse.fail(500, "原密码不能为空");
        }

        if (!StringUtils.hasText(req.getNewPassword())) {
            return ApiResponse.fail(500, "新密码不能为空");
        }

        if (req.getNewPassword().length() < 6) {
            return ApiResponse.fail(500, "新密码至少 6 位");
        }

        String querySql = "SELECT password FROM sys_user WHERE id = ? LIMIT 1";

        String dbPassword;
        try {
            dbPassword = jdbcTemplate.queryForObject(querySql, String.class, userId);
        } catch (Exception e) {
            return ApiResponse.fail(500, "未找到当前用户");
        }

        if (!req.getOldPassword().equals(dbPassword)) {
            return ApiResponse.fail(500, "原密码不正确");
        }

        String updateSql = "UPDATE sys_user SET password = ?, update_time = ? WHERE id = ?";
        int updated = jdbcTemplate.update(
                updateSql,
                req.getNewPassword(),
                LocalDateTime.now(),
                userId
        );

        if (updated <= 0) {
            return ApiResponse.fail(500, "密码修改失败");
        }

        return ApiResponse.success("密码修改成功", true);
    }

    /**
     * 上传头像
     */
    @PostMapping("/avatar/upload")
    public ApiResponse<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();

        if (userId == null || !StringUtils.hasText(username)) {
            return ApiResponse.fail(401, "未登录或登录已失效");
        }

        if (file == null || file.isEmpty()) {
            return ApiResponse.fail(500, "请选择要上传的图片");
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            return ApiResponse.fail(500, "头像图片不能超过 2MB");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = getFileExt(originalFilename);

        if (!isAllowedImageExt(ext)) {
            return ApiResponse.fail(500, "仅支持 png/jpg/jpeg 图片");
        }

        String dateDir = java.time.LocalDate.now().toString().replace("-", "");
        String safeUsername = username.trim();
        String newFileName = safeUsername + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        String uploadRoot = Paths.get(System.getProperty("user.dir"), "uploads", "avatar").toString();
        File saveDir = Paths.get(uploadRoot, dateDir).toFile();
        if (!saveDir.exists() && !saveDir.mkdirs()) {
            return ApiResponse.fail(500, "头像目录创建失败");
        }

        File dest = new File(saveDir, newFileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            return ApiResponse.fail(500, "头像上传失败：" + e.getMessage());
        }

        String relativePath = dateDir + "/" + newFileName;
        String avatarUrl = "/public/system/file/avatar/" + relativePath;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("avatarUrl", avatarUrl);
        data.put("fileName", newFileName);
        data.put("fileSize", file.getSize());

        return ApiResponse.success("头像上传成功", data);
    }

    /**
     * 统一将旧头像地址转成 public 路径
     */
    private String normalizeAvatarUrlToPublic(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return "";
        }

        String result = avatarUrl.trim();

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

    private String roleName(String roleCode) {
        if ("ADMIN".equalsIgnoreCase(roleCode)) {
            return "管理员";
        }
        return roleCode;
    }

    @Data
    public static class UserProfileUpdateReq {
        /**
         * 昵称
         */
        private String nickName;

        /**
         * 头像地址
         */
        private String avatarUrl;
    }

    @Data
    public static class PasswordUpdateReq {
        private String oldPassword;
        private String newPassword;
    }
}