package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器
 * 用于拦截需要登录才能访问的请求，检查用户是否已登录
 */
public class LoginInterceptor implements HandlerInterceptor {
    
    /**
     * 请求预处理方法，在Controller处理之前执行
     *
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @param handler 被调用的处理器
     * @return true-放行请求，false-拦截请求
     * @throws Exception 异常信息
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从ThreadLocal中获取当前用户信息
        if (UserHolder.getUser() == null) {
            // 用户未登录，返回401状态码并拦截请求
            response.setStatus(401);
            return false;
        }
        
        // 用户已登录，放行请求
        return true;
    }
}
