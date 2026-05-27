package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotblog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryShopReviews(Long shopId, Integer current);

    Result saveShopReview(Long shopId, String content);

    Result queryBlogOfFollow(Long max, Integer offset);

    Result queryHotBlogList(Long shopId, Long typeId, int limit);
}
