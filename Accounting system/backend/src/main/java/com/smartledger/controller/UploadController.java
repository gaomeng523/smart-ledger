package com.smartledger.controller;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.response.UploadResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传接口（V3 阶段二，拍照记账）。
 *
 * <p>图片保存到本地目录，返回可直接访问的相对地址。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    @Value("${smartledger.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 上传账单图片。
     */
    @PostMapping("/image")
    public R<UploadResultDTO> uploadImage(@RequestParam("file") MultipartFile file) {
        // 登录校验（未登录会抛 401）
        UserContext.requireOpenid();

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

        try {
            File dir = new File(uploadDir, subPath);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "图片目录创建失败");
            }
            file.transferTo(new File(dir, fileName).getAbsoluteFile());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("图片上传失败: {}", e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片上传失败，请重试");
        }

        String url = "/uploads/" + subPath + "/" + fileName;
        return R.ok(new UploadResultDTO(url));
    }

    private String resolveExt(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
}