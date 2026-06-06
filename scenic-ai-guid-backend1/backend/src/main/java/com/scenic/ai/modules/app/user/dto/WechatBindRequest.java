package com.scenic.ai.modules.app.user.dto;

public class WechatBindRequest {

    public String user_id;
    public String userId;

    public String code;

    public String getUserIdText() {
        if (user_id != null && !user_id.trim().isEmpty()) return user_id.trim();
        if (userId != null && !userId.trim().isEmpty()) return userId.trim();
        return "";
    }

    public String getCodeText() {
        return code == null ? "" : code.trim();
    }
}
