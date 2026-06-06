package com.scenic.ai.modules.app.user.dto;

import com.scenic.ai.modules.app.user.entity.TouristUser;

public class UserInfoDto {

    public Long id;
    public String user_id;
    public String nickname;
    public String phone;
    public String avatar_url;
    public String gender;
    public Integer age;
    public String register_source;

    public static UserInfoDto from(TouristUser user) {
        UserInfoDto dto = new UserInfoDto();
        dto.id = user.id;
        dto.user_id = user.userId;
        dto.nickname = user.nickname;
        dto.phone = user.phone;
        dto.avatar_url = user.avatarUrl;
        dto.gender = user.gender;
        dto.age = user.age;
        dto.register_source = user.registerSource;
        return dto;
    }
}