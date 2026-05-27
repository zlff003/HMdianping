package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;
    @Resource
    private IShopService shopService;

    @Override
    public Result queryHotblog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("create_time")
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        if (UserHolder.getUser() == null) {
            return;
        }
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user == null) {
            return;
        }
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        if (blog.getLiked() == null) {
            blog.setLiked(0);
        }
        if (blog.getComments() == null) {
            blog.setComments(0);
        }
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("发布失败");
        }
        pushBlogToFollowers(blog.getId(), user.getId());
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryShopReviews(Long shopId, Integer current) {
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        Page<Blog> page = query()
                .eq("shop_id", shopId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records, page.getTotal());
    }

    @Override
    public Result saveShopReview(Long shopId, String content) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        String reviewContent = StrUtil.trim(content);
        if (StrUtil.isBlank(reviewContent)) {
            return Result.fail("评论内容不能为空");
        }
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        Blog blog = new Blog();
        blog.setShopId(shopId);
        blog.setUserId(user.getId());
        blog.setTitle(buildReviewTitle(shop.getName(), reviewContent));
        blog.setContent(reviewContent);
        blog.setImages("");
        blog.setLiked(0);
        blog.setComments(0);
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("发布评价失败");
        }
        shopService.update().setSql("comments = comments + 1").eq("id", shopId).update();
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
        pushBlogToFollowers(blog.getId(), user.getId());
        queryBlogUser(blog);
        blog.setIsLike(false);
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        os = minTime == max ? os : os + offset;
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    private void pushBlogToFollowers(Long blogId, Long authorId) {
        List<Follow> follows = followService.query().eq("follow_user_id", authorId).list();
        for (Follow follow : follows) {
            String key = FEED_KEY + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blogId.toString(), System.currentTimeMillis());
        }
    }

    @Override
    public Result queryHotBlogList(Long shopId, Long typeId, int limit) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Blog> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (shopId != null) {
            qw.eq("shop_id", shopId);
        }
        if (typeId != null) {
            qw.eq("type_id", typeId);
        }
        List<Blog> records = query()
                .orderByDesc("liked")
                .orderByDesc("create_time")
                .last("LIMIT " + limit)
                .list();
        return Result.ok(records);
    }

    private String buildReviewTitle(String shopName, String content) {
        String summary = content.replaceAll("\\s+", " ").trim();
        if (summary.length() > 18) {
            summary = summary.substring(0, 18) + "...";
        }
        return shopName + " - " + summary;
    }
}
