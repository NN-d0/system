package com.radio.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应数据库表：sys_user
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 登录密码
     */
    private String password;

    /**
     * 真实姓名，对应数据库字段：real_name
     */
    private String realName;

    /**
     * 昵称，对应数据库字段：nick_name
     */
    private String nickName;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 头像地址，对应数据库字段：avatar_url
     */
    private String avatarUrl;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}