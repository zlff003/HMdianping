-- 秒杀优惠券的Lua脚本
-- 参数说明：
-- KEYS[1]: 秒杀优惠券库存key (seckill:stock:{voucherId})
-- KEYS[2]: 订单ID集合key (seckill:order:{voucherId})
-- ARGV[1]: 优惠券ID
-- ARGV[2]: 用户ID
-- ARGV[3]: 订单ID

-- 1. 获取库存
local stock = tonumber(redis.call('get', KEYS[1]))

-- 2. 判断库存是否充足
if stock <= 0 then
    -- 库存不足
    return 1
end

-- 3. 判断用户是否已经购买过（防止重复下单）
-- 使用set集合存储已购买的用户ID
if redis.call('sismember', KEYS[2], ARGV[2]) == 1 then
    -- 已经购买过，不允许重复下单
    return 2
end

-- 4. 扣减库存
redis.call('incrby', KEYS[1], -1)

-- 5. 将用户ID添加到已购买集合中
redis.call('sadd', KEYS[2], ARGV[2])

-- 6. 发送消息到Redis Stream（异步创建订单）
-- stream.orders 是Stream的名称
-- * 表示自动生成ID
-- voucherId, userId, id 是消息体的字段
redis.call('xadd', 'stream.orders', '*', 'voucherId', ARGV[1], 'userId', ARGV[2], 'id', ARGV[3])

-- 7. 返回成功
return 0