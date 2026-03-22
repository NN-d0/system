package com.radio.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * system 本地文件静态映射
 * 保留旧路径：
 *   /api/system/file/avatar/**
 * 新增公开路径：
 *   /public/system/file/avatar/**
 * 头像在 PC / APP 页面展示时，统一使用 public 路径，
 * 避免浏览器 <img> 请求走受保护的 /api 路径导致 401。
 */
@Configuration
public class SystemStaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String avatarUploadDir = Paths.get(System.getProperty("user.dir"), "uploads", "avatar")
                .toUri()
                .toString();

        registry.addResourceHandler("/api/system/file/avatar/**")
                .addResourceLocations(avatarUploadDir);

        registry.addResourceHandler("/public/system/file/avatar/**")
                .addResourceLocations(avatarUploadDir);
    }
}