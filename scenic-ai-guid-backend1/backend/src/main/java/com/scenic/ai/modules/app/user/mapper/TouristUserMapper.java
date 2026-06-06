package com.scenic.ai.modules.app.user.mapper;

import com.scenic.ai.modules.app.user.entity.TouristUser;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TouristUserMapper {

    @Select("""
        SELECT
          id,
          user_id,
          login_account,
          nickname,
          avatar_url,
          gender,
          age,
          age_group,
          phone,
          password_hash,
          register_source,
          last_area_id,
          last_spot_id,
          last_active_at,
          last_login_at,
          gps_authorized,
          profile_completed,
          status,
          deleted,
          created_at,
          updated_at
        FROM tourist_user
        WHERE login_account = #{loginAccount}
          AND deleted = 0
        LIMIT 1
    """)
    @Results(id = "TouristUserMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "login_account", property = "loginAccount"),
            @Result(column = "nickname", property = "nickname"),
            @Result(column = "avatar_url", property = "avatarUrl"),
            @Result(column = "gender", property = "gender"),
            @Result(column = "age", property = "age"),
            @Result(column = "age_group", property = "ageGroup"),
            @Result(column = "phone", property = "phone"),
            @Result(column = "password_hash", property = "passwordHash"),
            @Result(column = "register_source", property = "registerSource"),
            @Result(column = "last_area_id", property = "lastAreaId"),
            @Result(column = "last_spot_id", property = "lastSpotId"),
            @Result(column = "last_active_at", property = "lastActiveAt"),
            @Result(column = "last_login_at", property = "lastLoginAt"),
            @Result(column = "gps_authorized", property = "gpsAuthorized"),
            @Result(column = "profile_completed", property = "profileCompleted"),
            @Result(column = "status", property = "status"),
            @Result(column = "deleted", property = "deleted"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    TouristUser selectByLoginAccount(@Param("loginAccount") String loginAccount);

    @Select("""
        SELECT
          id,
          user_id,
          login_account,
          nickname,
          avatar_url,
          gender,
          age,
          age_group,
          phone,
          password_hash,
          register_source,
          last_area_id,
          last_spot_id,
          last_active_at,
          last_login_at,
          gps_authorized,
          profile_completed,
          status,
          deleted,
          created_at,
          updated_at
        FROM tourist_user
        WHERE user_id = #{userId}
          AND deleted = 0
        LIMIT 1
    """)
    @ResultMap("TouristUserMap")
    TouristUser selectByUserId(@Param("userId") String userId);

    @Insert("""
        INSERT INTO tourist_user (
          user_id,
          login_account,
          nickname,
          phone,
          password_hash,
          register_source,
          avatar_url,
          last_active_at,
          last_login_at,
          gps_authorized,
          profile_completed,
          status,
          deleted
        ) VALUES (
          #{user.userId},
          #{user.loginAccount},
          #{user.nickname},
          #{user.phone},
          #{user.passwordHash},
          #{user.registerSource},
          #{user.avatarUrl},
          NOW(),
          NOW(),
          #{user.gpsAuthorized},
          #{user.profileCompleted},
          #{user.status},
          #{user.deleted}
        )
    """)
    int insertTouristUser(@Param("user") TouristUser user);

    @Insert("""
        INSERT IGNORE INTO tourist_profile (
          user_id,
          source_type,
          created_at,
          updated_at
        ) VALUES (
          #{userId},
          'REGISTER',
          NOW(),
          NOW()
        )
    """)
    int insertProfileIfAbsent(@Param("userId") String userId);

    @Update("""
        UPDATE tourist_user
        SET last_login_at = NOW(),
            last_active_at = NOW()
        WHERE user_id = #{userId}
    """)
    int updateLoginTime(@Param("userId") String userId);
}