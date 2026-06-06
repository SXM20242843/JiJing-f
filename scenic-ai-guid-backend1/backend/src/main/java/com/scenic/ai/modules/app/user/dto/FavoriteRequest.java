package com.scenic.ai.modules.app.user.dto;

public class FavoriteRequest {

    public String user_id;
    public String userId;

    public String session_id;
    public String sessionId;

    public Long visit_id;
    public Long visitId;

    public String target_type;
    public String targetType;

    public Long target_id;
    public Long targetId;

    public String area_code;
    public String areaCode;

    public String scene_code;
    public String sceneCode;

    public String source_page;
    public String sourcePage;

    public String content;
    public String name;

    public String getUserIdText() {
        if (user_id != null && !user_id.trim().isEmpty()) return user_id.trim();
        if (userId != null && !userId.trim().isEmpty()) return userId.trim();
        return "";
    }

    public String getSessionIdText() {
        if (session_id != null && !session_id.trim().isEmpty()) return session_id.trim();
        if (sessionId != null && !sessionId.trim().isEmpty()) return sessionId.trim();
        return "";
    }

    public Long getVisitIdValue() {
        return visit_id != null ? visit_id : visitId;
    }

    public String getTargetTypeText() {
        if (target_type != null && !target_type.trim().isEmpty()) {
            return target_type.trim().toUpperCase();
        }
        if (targetType != null && !targetType.trim().isEmpty()) {
            return targetType.trim().toUpperCase();
        }

        if (!getSceneCodeText().isEmpty()) {
            return "SPOT";
        }

        if (!getAreaCodeText().isEmpty()) {
            return "AREA";
        }

        return "";
    }

    public Long getTargetIdValue() {
        return target_id != null ? target_id : targetId;
    }

    public String getAreaCodeText() {
        if (area_code != null && !area_code.trim().isEmpty()) return area_code.trim();
        if (areaCode != null && !areaCode.trim().isEmpty()) return areaCode.trim();
        return "";
    }

    public String getSceneCodeText() {
        if (scene_code != null && !scene_code.trim().isEmpty()) return scene_code.trim();
        if (sceneCode != null && !sceneCode.trim().isEmpty()) return sceneCode.trim();
        return "";
    }

    public String getSourcePageText() {
        if (source_page != null && !source_page.trim().isEmpty()) return source_page.trim();
        if (sourcePage != null && !sourcePage.trim().isEmpty()) return sourcePage.trim();
        return "favorite";
    }

    public String getContentText() {
        if (content != null && !content.trim().isEmpty()) return content.trim();
        if (name != null && !name.trim().isEmpty()) return name.trim();
        return "";
    }
}
