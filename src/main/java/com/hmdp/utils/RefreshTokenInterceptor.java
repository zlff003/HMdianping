package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Token刷新拦截器
 * 用于在每次请求时刷新用户Token的有效期，实现自动续期功能
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    
    /**
     * 请求预处理方法，在Controller处理之前执行
     * 主要功能：从请求头获取Token，验证用户身份，刷新Token有效期
     *
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @param handler 被调用的处理器
     * @return true-放行请求，false-拦截请求
     * @throws Exception 异常信息
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头中获取Authorization Token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // Token为空，直接放行（由LoginInterceptor判断是否需要登录）
            return true;
        }
        
        // 2. 从Redis中获取用户信息（Hash结构）
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().
                entries(RedisConstants.LOGIN_USER_KEY + token);
        
        if (userMap.isEmpty()) {
            // Redis中没有该用户信息，直接放行（由LoginInterceptor判断）
            return true;
        }

        // 3. 将查询到的Hash数据转换为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 4. 将用户信息保存到ThreadLocal，供后续业务使用
        UserHolder.saveUser(userDTO);

        // 5. 刷新Token的有效期（实现自动续期）
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        
        // 6. 放行请求
        return true;
    }

    /**
     * 请求完成后执行的方法
     * 注意：当前未实现清理ThreadLocal的逻辑，建议在LoginInterceptor中实现
     *
     * @param request HTTP请求对象
     * @param response HTTP响应对象
     * @param handler 被调用的处理器
     * @param ex 异常信息
     * @throws Exception 异常信息
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // TODO: 建议在此处或LoginInterceptor中清理ThreadLocal，防止内存泄漏
        // UserHolder.removeUser();
    }
}
