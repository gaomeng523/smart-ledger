package com.smartledger.controller;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.response.UploadResultDTO;
import com.smartledger.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 图片上传接口。
 *
 * <p>图片保存到本地目录，返回可直接访问的相对地址。拍照识别见 /api/bill/parse-image。</p>
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final ImageStorageService imageStorageService;

    /**
     * 上传账单图片（仅存图，不做识别）。
     */
    @PostMapping("/image")
    public R<UploadResultDTO> uploadImage(@RequestParam("file") MultipartFile file) {
        // 登录校验（未登录会抛 401）
        UserContext.requireOpenid();
        return R.ok(new UploadResultDTO(imageStorageService.store(file).url()));
    }

    /**
     * 鉴权读取小票图片：不走 nginx 静态目录，避免链接被转发后任何人都能看。
     *
     * @param path 形如 /uploads/2026/11/xxx.jpg
     */
    @GetMapping("/image")
    public ResponseEntity<byte[]> viewImage(@RequestParam("path") String path) {
        UserContext.requireOpenid();
        Path file = imageStorageService.resolvePath(path);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片读取失败");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imageStorageService.mimeType(path)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(bytes);
    }
}
