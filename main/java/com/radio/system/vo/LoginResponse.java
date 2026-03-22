package com.radio.system.vo;

import lombok.Data;

/**
 * 登录返回结果
 */
@Data
public class LoginResponse {

    private String token;
    private String tokenType;
    private Long expireSeconds;
    private UserInfoVO userInfo;
}