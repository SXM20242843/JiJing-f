package com.scenic.ai.modules.app.user.dto;

import java.time.LocalDateTime;

public class FavoriteItemDto {

    public Long id;
    public String user_id;
    public String target_type;
    public Long target_id;
    public Integer status;

    public String target_name;
    public String intro;
    public String image_url;
    public String area_code;
    public String scene_code;

    public LocalDateTime created_at;
    public LocalDateTime updated_at;
}