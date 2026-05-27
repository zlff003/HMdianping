package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.Follow;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

@SpringBootTest
class ReferenceDataSeederTests {

    private static final String TITLE_PREFIX = "[参考数据]";
    private static final String COMMENT_PREFIX = "[参考数据]";
    private static final List<Long> AUTHOR_IDS = Arrays.asList(901L, 902L, 903L, 904L, 905L, 906L, 907L, 908L);
    private static final List<Long> INTERACTOR_IDS = Arrays.asList(909L, 910L, 911L, 912L, 913L, 914L, 915L, 916L, 917L, 918L, 919L, 920L);
    private static final List<String> TITLE_TEMPLATES = Arrays.asList(
            "下班后值得专门跑一趟",
            "朋友聚餐基本不会踩雷",
            "人均友好，味道在线",
            "想二刷的一家店",
            "工作日午餐好选择",
            "环境比预期更舒服",
            "带外地朋友来也稳",
            "这家我愿意收藏"
    );
    private static final List<String> CONTENT_TEMPLATES = Arrays.asList(
            "这次主要冲着招牌菜来的，结果出品和服务都比预想稳定，适合第一次来直接点经典款。",
            "整体节奏很舒服，等位和上菜都不拖，口味属于大多数人都能接受的那种稳妥路线。",
            "更适合朋友小聚或者周末随便吃一顿，不会特别惊艳，但完成度挺高，性价比也在线。",
            "环境收拾得比较利落，菜量正常偏足，适合两三个人一起点几样慢慢吃，体验感不错。",
            "如果附近不知道吃什么，这家值得优先考虑，属于会愿意再次回访并推荐给同事的类型。"
    );
    private static final List<String> COMMENT_TEMPLATES = Arrays.asList(
            "刚去过一次，整体比我预期稳很多。",
            "我更喜欢它家的环境，适合慢慢坐着吃。",
            "价格还可以接受，味道没有掉链子。",
            "适合聚餐，人多点菜会更划算。",
            "服务响应挺快，这点加分不少。",
            "我会更推荐第一次去点招牌菜。"
    );

    @Resource
    private BlogMapper blogMapper;
    @Resource
    private BlogCommentsMapper blogCommentsMapper;
    @Resource
    private FollowMapper followMapper;
    @Resource
    private ShopMapper shopMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void seedReferenceData() {
        Random random = new Random(20260427L);
        List<User> authors = fetchUsers(AUTHOR_IDS);
        List<User> interactors = fetchUsers(INTERACTOR_IDS);
        List<Shop> shops = shopMapper.selectList(new QueryWrapper<Shop>().orderByAsc("id"));
        if (authors.size() != AUTHOR_IDS.size() || interactors.isEmpty() || shops.isEmpty()) {
            throw new IllegalStateException("示例用户或店铺数据不足，无法生成参考数据");
        }

        cleanupSeedData();
        createFollowRelations();
        List<Blog> seedBlogs = createBlogs(authors, shops, random);
        createComments(seedBlogs, interactors, random);
        seedBlogLikes(seedBlogs, interactors, authors, random);
        rebuildFeeds(seedBlogs);
    }

    @Test
    void printSeedSummary() {
        Long blogCount = blogMapper.selectCount(new QueryWrapper<Blog>().likeRight("title", TITLE_PREFIX)).longValue();
        Long commentCount = blogCommentsMapper.selectCount(new QueryWrapper<BlogComments>().likeRight("content", COMMENT_PREFIX)).longValue();
        Long followCount = followMapper.selectCount(new QueryWrapper<Follow>()
                .in("user_id", INTERACTOR_IDS)
                .in("follow_user_id", AUTHOR_IDS)).longValue();
        Set<String> likedKeys = stringRedisTemplate.keys(BLOG_LIKED_KEY + "*");
        Set<String> feedKeys = stringRedisTemplate.keys(FEED_KEY + "*");
        System.out.println("seed_blogs=" + blogCount);
        System.out.println("seed_comments=" + commentCount);
        System.out.println("seed_follows=" + followCount);
        System.out.println("redis_liked_keys=" + (likedKeys == null ? 0 : likedKeys.size()));
        System.out.println("redis_feed_keys=" + (feedKeys == null ? 0 : feedKeys.size()));
    }

    private List<User> fetchUsers(List<Long> ids) {
        return userMapper.selectBatchIds(ids);
    }

    private void cleanupSeedData() {
        List<Blog> oldBlogs = blogMapper.selectList(new QueryWrapper<Blog>().likeRight("title", TITLE_PREFIX));
        List<Long> oldBlogIds = oldBlogs.stream().map(Blog::getId).collect(Collectors.toList());
        if (!oldBlogIds.isEmpty()) {
            blogCommentsMapper.delete(new QueryWrapper<BlogComments>().in("blog_id", oldBlogIds));
            oldBlogIds.forEach(id -> stringRedisTemplate.delete(BLOG_LIKED_KEY + id));
            blogMapper.deleteBatchIds(oldBlogIds);
        }
        followMapper.delete(new QueryWrapper<Follow>()
                .in("user_id", INTERACTOR_IDS)
                .in("follow_user_id", AUTHOR_IDS));
        Set<String> feedKeys = stringRedisTemplate.keys(FEED_KEY + "*");
        if (feedKeys != null && !feedKeys.isEmpty()) {
            stringRedisTemplate.delete(feedKeys);
        }
    }

    private void createFollowRelations() {
        List<Follow> follows = new ArrayList<>();
        for (int i = 0; i < INTERACTOR_IDS.size(); i++) {
            Long followerId = INTERACTOR_IDS.get(i);
            Long firstAuthor = AUTHOR_IDS.get(i % AUTHOR_IDS.size());
            Long secondAuthor = AUTHOR_IDS.get((i + 3) % AUTHOR_IDS.size());
            follows.add(buildFollow(followerId, firstAuthor, i));
            if (!secondAuthor.equals(firstAuthor)) {
                follows.add(buildFollow(followerId, secondAuthor, i + 20));
            }
        }
        follows.forEach(followMapper::insert);
    }

    private Follow buildFollow(Long userId, Long followUserId, int offsetDays) {
        return new Follow()
                .setUserId(userId)
                .setFollowUserId(followUserId)
                .setCreateTime(LocalDateTime.now().minusDays(offsetDays % 9).minusHours(offsetDays % 6));
    }

    private List<Blog> createBlogs(List<User> authors, List<Shop> shops, Random random) {
        List<Blog> blogs = new ArrayList<>();
        int sequence = 0;
        for (User author : authors) {
            for (int i = 0; i < 4; i++) {
                Shop shop = shops.get((sequence + i) % shops.size());
                LocalDateTime createTime = LocalDateTime.now()
                        .minusDays(sequence % 12)
                        .minusHours((sequence * 3) % 23)
                        .minusMinutes((sequence * 7) % 55);
                Blog blog = new Blog()
                        .setShopId(shop.getId())
                        .setUserId(author.getId())
                        .setTitle(TITLE_PREFIX + shop.getName() + " - " + TITLE_TEMPLATES.get(sequence % TITLE_TEMPLATES.size()))
                        .setImages(pickImages(shop.getImages(), random))
                        .setContent(buildContent(shop.getName(), sequence))
                        .setLiked(0)
                        .setComments(0)
                        .setCreateTime(createTime)
                        .setUpdateTime(createTime);
                blogMapper.insert(blog);
                blogs.add(blog);
                sequence++;
            }
        }
        return blogs;
    }

    private String pickImages(String shopImages, Random random) {
        if (shopImages == null || shopImages.trim().isEmpty()) {
            return "";
        }
        List<String> images = Arrays.stream(shopImages.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (images.isEmpty()) {
            return "";
        }
        Collections.rotate(images, random.nextInt(images.size()));
        int count = Math.min(images.size(), 1 + random.nextInt(Math.min(3, images.size())));
        return String.join(",", images.subList(0, count));
    }

    private String buildContent(String shopName, int sequence) {
        StringBuilder builder = new StringBuilder();
        builder.append(TITLE_PREFIX).append(" ").append(shopName).append("\n");
        builder.append(CONTENT_TEMPLATES.get(sequence % CONTENT_TEMPLATES.size())).append("\n");
        builder.append("这次重点体验了招牌口味、环境和服务流程，整体属于会愿意再来的那一类店。");
        return builder.toString();
    }

    private void createComments(List<Blog> blogs, List<User> interactors, Random random) {
        for (int i = 0; i < blogs.size(); i++) {
            Blog blog = blogs.get(i);
            int commentCount = 2 + random.nextInt(5);
            for (int j = 0; j < commentCount; j++) {
                User commenter = interactors.get((i + j) % interactors.size());
                LocalDateTime createTime = blog.getCreateTime().plusHours(j + 1L);
                BlogComments comment = new BlogComments()
                        .setUserId(commenter.getId())
                        .setBlogId(blog.getId())
                        .setParentId(0L)
                        .setAnswerId(0L)
                        .setContent(COMMENT_PREFIX + COMMENT_TEMPLATES.get((i + j) % COMMENT_TEMPLATES.size()))
                        .setLiked(random.nextInt(8))
                        .setStatus(false)
                        .setCreateTime(createTime)
                        .setUpdateTime(createTime);
                blogCommentsMapper.insert(comment);
            }
            blogMapper.update(null, new UpdateWrapper<Blog>()
                    .eq("id", blog.getId())
                    .set("comments", commentCount)
                    .set("update_time", blog.getUpdateTime()));
            blog.setComments(commentCount);
        }
    }

    private void seedBlogLikes(List<Blog> seedBlogs, List<User> interactors, List<User> authors, Random random) {
        List<Long> candidateUserIds = new ArrayList<>();
        candidateUserIds.addAll(authors.stream().map(User::getId).collect(Collectors.toList()));
        candidateUserIds.addAll(interactors.stream().map(User::getId).collect(Collectors.toList()));
        for (int i = 0; i < seedBlogs.size(); i++) {
            Blog blog = seedBlogs.get(i);
            int likeCount = 6 + random.nextInt(18);
            Set<Long> likedUsers = pickUniqueUsers(candidateUserIds, likeCount, random);
            String key = BLOG_LIKED_KEY + blog.getId();
            LocalDateTime baseTime = blog.getCreateTime().plusMinutes(10);
            int offset = 0;
            for (Long userId : likedUsers) {
                long score = baseTime.plusMinutes(offset).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), score);
                offset++;
            }
            blogMapper.update(null, new UpdateWrapper<Blog>()
                    .eq("id", blog.getId())
                    .set("liked", likedUsers.size())
                    .set("update_time", blog.getUpdateTime()));
        }
    }

    private Set<Long> pickUniqueUsers(List<Long> candidateUserIds, int likeCount, Random random) {
        List<Long> shuffled = new ArrayList<>(candidateUserIds);
        Collections.shuffle(shuffled, random);
        return new HashSet<>(shuffled.subList(0, Math.min(likeCount, shuffled.size())));
    }

    private void rebuildFeeds(List<Blog> seedBlogs) {
        Map<Long, List<Long>> authorFollowersMap = new HashMap<>();
        List<Follow> follows = followMapper.selectList(new QueryWrapper<Follow>()
                .in("user_id", INTERACTOR_IDS)
                .in("follow_user_id", AUTHOR_IDS));
        for (Follow follow : follows) {
            authorFollowersMap.computeIfAbsent(follow.getFollowUserId(), k -> new ArrayList<>()).add(follow.getUserId());
        }
        for (Blog blog : seedBlogs) {
            List<Long> followers = authorFollowersMap.get(blog.getUserId());
            if (followers == null || followers.isEmpty()) {
                continue;
            }
            long score = blog.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            for (Long followerId : followers) {
                stringRedisTemplate.opsForZSet().add(FEED_KEY + followerId, blog.getId().toString(), score);
                stringRedisTemplate.expire(FEED_KEY + followerId, 30, TimeUnit.DAYS);
            }
        }
    }
}
