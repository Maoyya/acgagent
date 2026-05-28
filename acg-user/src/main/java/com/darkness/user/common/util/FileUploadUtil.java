package com.darkness.user.common.util;

import com.darkness.common.exception.BizException;
import com.darkness.common.result.ResultCode;
import com.darkness.user.common.config.FileUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传工具类，处理头像等图片文件的上传逻辑。
 * <p>
 * 校验文件非空、大小不超过 2MB、Content-Type 在允许范围内，
 * 生成 UUID 文件名保存到本地目录，返回可访问的 URL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadUtil {

    private final FileUploadConfig fileUploadConfig;

    /** 允许上传的图片 Content-Type 集合 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    /** 文件大小上限：2MB */
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

    /**
     * 保存上传文件到本地文件系统。
     * 校验文件非空、大小不超过 2MB、Content-Type 为图片格式，
     * 生成 UUID 文件名（保留原始扩展名）后保存，返回访问 URL。
     *
     * @param file 上传的文件，不能为空
     * @return 文件访问 URL（urlPrefix + "/" + UUID 文件名）
     * @throws BizException 文件为空、大小超限、类型不允许或保存失败时抛出
     */
    public String saveFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件大小不能超过 2MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的文件类型，仅支持 JPEG、PNG、GIF、WebP 格式");
        }

        // 生成 UUID 文件名，保留原始扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString() + extension;

        // 创建目标目录（如不存在）
        Path dirPath = Paths.get(fileUploadConfig.getDir());
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", dirPath, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件保存失败");
        }

        // 保存文件
        Path targetPath = dirPath.resolve(filename);
        try {
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            log.error("保存上传文件失败: {}", targetPath, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件保存失败");
        }

        return fileUploadConfig.getUrlPrefix() + "/" + filename;
    }
}
