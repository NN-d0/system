package com.radio.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 密码修改请求
 */
@Data
public class PasswordUpdateRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}