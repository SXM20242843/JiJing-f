package com.scenic.ai.modules.app.user.dto;

public class LoginResponse {

    public String token;
    public UserInfoDto userInfo;

    public LoginResponse() {
    }

    public LoginResponse(String token, UserInfoDto userInfo) {
        this.token = token;
        this.userInfo = userInfo;
    }
}