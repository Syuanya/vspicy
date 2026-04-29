package com.vspicy.video.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 浏览器会自动请求 /favicon.ico。
 * 后端服务没有静态 favicon 时，Spring MVC 会抛 NoResourceFoundException，
 * 进而被全局异常处理器记录为“系统异常”。
 *
 * 这里直接返回 204，避免无意义错误日志。
 */
@RestController
public class FaviconController {

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }
}
