package com.vspicy.common.web;

import com.vspicy.common.core.Result;
import com.vspicy.common.exception.BizException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return Result.fail(ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        log.warn("请求参数缺失: {}", ex.getMessage());
        return Result.fail(400, "缺少请求参数：" + ex.getParameterName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超过 multipart 限制: {}", ex.getMessage());
        return Result.fail(413, "上传文件过大，请检查分片大小或服务端 multipart 配置");
    }

    @ExceptionHandler(MultipartException.class)
    public Result<Void> handleMultipartException(MultipartException ex) {
        log.warn("上传请求解析失败: {}", ex.getMessage());
        return Result.fail(400, "上传请求格式错误或缺少上传字段，请检查 file、chunkIndex、totalChunks 等 multipart 参数");
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException ex, HttpServletResponse response) {
        log.debug("异步请求超时: {}", ex.getMessage());
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException ex, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        String resourcePath = ex.getResourcePath();
        if ("favicon.ico".equals(resourcePath) || "/favicon.ico".equals(resourcePath)) {
            log.debug("忽略浏览器 favicon 探测请求: {}", resourcePath);
            return Result.fail(404, "资源不存在");
        }
        log.warn("资源不存在: {}", resourcePath);
        return Result.fail(404, "资源不存在: " + resourcePath);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return Result.fail(500, "系统异常: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
    }
}
