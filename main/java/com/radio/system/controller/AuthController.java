package com.radio.system.controller;

import com.radio.system.common.ApiResponse;
import com.radio.system.dto.LoginRequest;
import com.radio.system.service.AuthService;
import com.radio.system.vo.LoginResponse;
import com.radio.system.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 */
@RestController
@RequestMapping("/api/system/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("登录成功", authService.login(request));
    }

    /**
     * 当前登录用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserInfoVO> me() {
        return ApiResponse.success(authService.currentUser());
    }
}