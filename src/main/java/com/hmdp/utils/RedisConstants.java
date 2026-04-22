package com.hmdp.utils;

/**
 * Redis常量类
 * 定义项目中使用的Redis键前缀和过期时间等常量
 */
public class RedisConstants {
    
    // ==================== 用户相关 ====================
    
    /**
     * 登录验证码的Redis键前缀
     * 完整键格式：login:code:{phone}
     */
    public static final String LOGIN_CODE_KEY = "login:code:";
    
    /**
     * 登录验证码的过期时间（分钟）
     */
    public static final Long LOGIN_CODE_TTL = 2L;
    
    /**
     * 登录用户的Redis键前缀
     * 完整键格式：login:token:{token}
     */
    public static final String LOGIN_USER_KEY = "login:token:";
    
    /**
     * 登录用户的过期时间（分钟）= 25天
     */
    public static final Long LOGIN_USER_TTL = 36000L;

    // ==================== 缓存相关 ====================
    
    /**
     * 缓存空值的过期时间（分钟）- 用于解决缓存穿透
     */
    public static final Long CACHE_NULL_TTL = 2L;

    /**
     * 店铺缓存的过期时间（分钟）
     */
    public static final Long CACHE_SHOP_TTL = 30L;
    
    /**
     * 店铺缓存的Redis键前缀
     * 完整键格式：cache:shop:{shopId}
     */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    // ==================== 分布式锁相关 ====================
    
    /**
     * 店铺分布式锁的Redis键前缀
     * 完整键格式：lock:shop:{shopId}
     */
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    
    /**
     * 店铺分布式锁的过期时间（秒）
     */
    public static final Long LOCK_SHOP_TTL = 10L;

    // ==================== 其他业务相关 ====================
    
    /**
     * 秒杀库存的Redis键前缀
     */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    
    /**
     * 博客点赞的Redis键前缀
     */
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    
    /**
     * 推送Feed流的Redis键前缀
     */
    public static final String FEED_KEY = "feed:";
    
    /**
     * 店铺地理位置的Redis键前缀（GEO）
     */
    public static final String SHOP_GEO_KEY = "shop:geo:";
    
    /**
     * 用户签到记录的Redis键前缀（Bitmap）
     */
    public static final String USER_SIGN_KEY = "sign:";
}
