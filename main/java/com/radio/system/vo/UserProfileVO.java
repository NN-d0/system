package com.radio.system.vo;

import lombok.Data;

/**
 * 用户个人资料
 */
@Data
public class UserProfileVO {

    private Long id;
    private String username;
    private String nickName;
    private String avatarUrl;
}