package com.smartledger.service;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 账单图片落盘：校验格式与大小，按「年/月」分目录存储，返回相对访问地址。
 */
@Slf4j
@Component
public class ImageStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Map<String, String> MIME_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif");
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    @Value("${smartledger.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 保存图片并返回地址与内容，便于调用方直接送去识别，避免二次读盘。
     */
    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能超过 5MB");
        }
        String ext = resolveExt(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 jpg/png/webp/gif 格式图片");
        }

        LocalDate now = LocalDate.now();
        String subPath = now.getYear() + "/" + String.format("%02d", now.getMonthValue());
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        byte[] bytes;
        try {
            bytes = file.getBytes();
            if (!matchesMagic(bytes, ext)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "图片内容与扩展名不匹配");
            }
            java.io.File dir = new java.io.File(uploadDir, subPath);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "图片目录创建失败");
            }
            Files.write(new java.io.File(dir, fileName).toPath(), bytes);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("图片保存失败: {}", e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片保存失败，请重试");
        }

        String url = "/uploads/" + subPath + "/" + fileName;
        return new StoredImage(url, MIME_TYPES.get(ext), bytes);
    }

    /**
     * 将相对地址解析为磁盘路径，供鉴权后的读取接口使用。
     * 拒绝 ../ 等越界路径，且只允许上传目录下的图片文件。
     */
    public java.nio.file.Path resolvePath(String url) {
        if (url == null || !url.startsWith("/uploads/") || !ALLOWED_EXT.contains(resolveExt(url))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址不合法");
        }
        java.nio.file.Path base = java.nio.file.Paths.get(uploadDir).toAbsolutePath().normalize();
        java.nio.file.Path target = base.resolve(url.substring("/uploads/".length())).normalize();
        if (!target.startsWith(base) || !Files.isRegularFile(target)) {
            throw new BizException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        return target;
    }

    /**
     * 由相对地址推断 MIME 类型。
     */
    public String mimeType(String url) {
        return MIME_TYPES.getOrDefault(resolveExt(url), "application/octet-stream");
    }

    /**
     * 校验文件头，防止把任意文件改扩展名后上传。
     */
    private boolean matchesMagic(byte[] b, String ext) {
        if (b == null || b.length < 12) {
            return false;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
            case "png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "gif" -> b[0] == 'G' && b[1] == 'I' && b[2] == 'F';
            case "webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    private String resolveExt(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    /**
     * 已落盘的图片：相对地址、MIME 类型、原始字节。
     */
    public record StoredImage(String url, String mimeType, byte[] bytes) {
    }
}
