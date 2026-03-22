package com.radio.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 个人资料更新请求
 */
@Data
public class UserProfileUpdateRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "昵称不能为空")
    private String nickName;

    private String avatarUrl;
}