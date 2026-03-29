package com.mickey.onlineordering.onlineorderingserver.controller;

import com.mickey.onlineordering.onlineorderingserver.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 文件上传控制器
 */
@Slf4j
@Tag(name = "文件上传接口", description = "文件上传相关接口")
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    /**
     * 上传聊天图片
     */
    @Operation(summary = "上传聊天图片")
    @PostMapping("/chat-image")
    public Result<String> uploadChatImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }

        try {
            // 获取原始文件名
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return Result.error(400, "文件名不能为空");
            }

            // 验证文件类型
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            if (!isImageFile(extension)) {
                return Result.error(400, "只支持图片格式（jpg、jpeg、png、gif）");
            }

            // 生成唯一文件名
            String fileName = UUID.randomUUID().toString() + extension;

            // 获取项目根目录的绝对路径
            String projectPath = System.getProperty("user.dir");
            String uploadDir = projectPath + File.separator + "uploads" + File.separator + "chat";
            
            log.info("项目根目录: {}", projectPath);
            log.info("上传目录: {}", uploadDir);

            // 确保上传目录存在
            File uploadDirFile = new File(uploadDir);
            if (!uploadDirFile.exists()) {
                boolean created = uploadDirFile.mkdirs();
                log.info("创建上传目录: {} - {}", uploadDir, created ? "成功" : "失败");
            }

            // 保存文件
            File destFile = new File(uploadDir, fileName);
            file.transferTo(destFile);

            // 返回访问URL
            String fileUrl = "/uploads/chat/" + fileName;
            log.info("文件上传成功: {} -> {}", originalFilename, destFile.getAbsolutePath());

            return Result.success(fileUrl);

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error(500, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 验证是否为图片文件
     */
    private boolean isImageFile(String extension) {
        return extension.equalsIgnoreCase(".jpg") ||
               extension.equalsIgnoreCase(".jpeg") ||
               extension.equalsIgnoreCase(".png") ||
               extension.equalsIgnoreCase(".gif");
    }
}

