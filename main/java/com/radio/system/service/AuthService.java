package com.radio.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.radio.system.context.UserContext;
import com.radio.system.dto.LoginRequest;
import com.radio.system.entity.SysUser;
import com.radio.system.exception.BusinessException;
import com.radio.system.mapper.SysUserMapper;
import com.radio.system.util.JwtUtil;
import com.radio.system.vo.LoginResponse;
import com.radio.system.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 登录相关业务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
                        .last("limit 1")
        );

        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        if (!user.getPassword().equals(request.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "当前用户已被禁用");
        }

        String token = jwtUtil.createToken(user.getId(), user.getUsername());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpireSeconds(jwtUtil.getExpireSeconds());
        response.setUserInfo(convertToUserInfo(user));
        return response;
    }

    public UserInfoVO currentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return convertToUserInfo(user);
    }

    private UserInfoVO convertToUserInfo(SysUser user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRoleCode(user.getRoleCode());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        return vo;
    }
}