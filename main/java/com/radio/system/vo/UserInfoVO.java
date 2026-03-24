package com.radio.system.vo;

import lombok.Data;

/**
 * 当前登录用户信息
 */
@Data
public class UserInfoVO {

    private Long id;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 头像地址
     */
    private String avatarUrl;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 用户状态
     */
    private Integer status;
}