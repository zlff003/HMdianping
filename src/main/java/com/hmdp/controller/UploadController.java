package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            if (image == null || image.isEmpty()) {
                return Result.fail("上传文件不能为空");
            }
            String originalFilename = image.getOriginalFilename();
            if (StrUtil.isBlank(originalFilename)) {
                return Result.fail("文件名不能为空");
            }
            String relativePath = createNewFileName(originalFilename);
            File targetFile = new File(SystemConstants.IMAGE_UPLOAD_DIR, relativePath);
            image.transferTo(targetFile);
            String accessPath = "/imgs/" + relativePath.replace(File.separatorChar, '/');
            log.debug("文件上传成功：{}", accessPath);
            return Result.ok(accessPath);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        String relativePath = normalizeRelativeFilename(filename);
        if (StrUtil.isBlank(relativePath)) {
            return Result.fail("错误的文件名称");
        }
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, relativePath);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename) {
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return StrUtil.format("blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }

    private String normalizeRelativeFilename(String filename) {
        if (StrUtil.isBlank(filename)) {
            return null;
        }
        String normalized = filename.trim().replace('\\', '/');
        if (normalized.startsWith("/imgs/")) {
            normalized = normalized.substring("/imgs/".length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
