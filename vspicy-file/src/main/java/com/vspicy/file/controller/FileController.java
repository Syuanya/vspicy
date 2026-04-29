package com.vspicy.file.controller;

import com.vspicy.common.core.Result;
import com.vspicy.file.dto.FileUploadResponse;
import com.vspicy.file.service.FileStorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public Result<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false, defaultValue = "COMMON") String bizType,
            @RequestParam(value = "uploaderId", required = false, defaultValue = "1") Long uploaderId
    ) {
        return Result.ok(fileStorageService.upload(file, bizType, uploaderId));
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("vspicy-file ok");
    }
}
