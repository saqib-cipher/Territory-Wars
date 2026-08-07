package com.territorywars.network;

public class ApiError {
    public int code;
    public String message;

    public ApiError(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
