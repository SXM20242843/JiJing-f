package com.scenic.ai.modules.app.user.entity;

import java.time.LocalDateTime;

public class TouristUser {

    public Long id;
    public String userId;
    public String loginAccount;
    public String nickname;
    public String avatarUrl;
    public String gender;
    public Integer age;
    public String ageGroup;
    public String phone;
    public String passwordHash;
    public String registerSource;
    public Long lastAreaId;
    public Long lastSpotId;
    public LocalDateTime lastActiveAt;
    public LocalDateTime lastLoginAt;
    public Integer gpsAuthorized;
    public Integer profileCompleted;
    public Integer status;
    public Integer deleted;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}