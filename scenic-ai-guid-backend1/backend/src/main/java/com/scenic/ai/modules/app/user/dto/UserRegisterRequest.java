package com.scenic.ai.modules.app.user.dto;

public class UserRegisterRequest {

    public String account;
    public String username;
    public String phone;
    public String nickname;
    public String password;

    public String visitor_id;
    public String visitorId;

    public String session_id;
    public String sessionId;

    public String getAccountText() {
        if (account != null && !account.trim().isEmpty()) {
            return account.trim();
        }
        if (username != null && !username.trim().isEmpty()) {
            return username.trim();
        }
        if (phone != null && !phone.trim().isEmpty()) {
            return phone.trim();
        }
        return "";
    }

    public String getNicknameText() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        return getAccountText();
    }
}