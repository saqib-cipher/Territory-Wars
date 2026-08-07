package com.territorywars.network;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Wrapper produced by the API when a request fails.
 */
public class ApiError {

    @SerializedName("code")
    public int code;

    @SerializedName("message")
    public String message;

    @SerializedName("details")
    public Object details;

    @Override
    public String toString() {
        return message != null ? message : "Unknown error (" + code + ")";
    }
}