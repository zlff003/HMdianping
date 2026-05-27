package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer ;

import jakarta.annotation.Resource;

/**
 * MVC配置类
 * 配置拦截器链，实现Token刷新和登录验证功能
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 添加拦截器配置
     * 拦截器执行顺序：RefreshTokenInterceptor(order=0) -> LoginInterceptor(order=1)
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        // 1. 登录拦截器：拦截需要登录才能访问的请求
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",      // 店铺查询（无需登录）
                        "/voucher/**",   // 优惠券查询（无需登录）
                        "/shop-type/**", // 店铺类型查询（无需登录）
                        "/upload/**",    // 文件上传（无需登录）
                        "/blog/hot",     // 热门博客（无需登录）
                        "/user/code",    // 发送验证码（无需登录）
                        "/user/login",   // 用户登录（无需登录）
                        "/ai/**",        // AI 客服 + 知识库管理接口（无需登录）
                        "/mcp/**"        // MCP Server 端点（外部 MCP Client 连接）
                ).order(1);  // 执行顺序：第2个
        
        // 2. Token刷新拦截器：拦截所有请求，刷新Token有效期
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")  // 拦截所有路径
                .order(0);  // 执行顺序：第1个（先刷新Token，再判断是否登录）
    }
}
