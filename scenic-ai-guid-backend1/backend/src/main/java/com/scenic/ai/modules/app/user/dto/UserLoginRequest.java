package com.scenic.ai.modules.app.user.dto;

public class UserLoginRequest {

    public String account;
    public String username;
    public String phone;
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
}