package com.vspicy.common.core;

import java.io.Serializable;

public record Result<T>(int code, String message, T data) implements Serializable {
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
