package com.radio.system.vo;


import lombok.Data;

@Data
public class UserInfoVO {
    private Long id;
    private String username;
    private String realName;
    private String roleCode;
    private String phone;
    private Integer status;
}
