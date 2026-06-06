package com.scenic.ai.modules.app.user.mapper;

import com.scenic.ai.modules.app.user.entity.TouristProfileInfo;
import com.scenic.ai.modules.app.route.dto.profile.UserProfileAiContextResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserProfileMapper {

    @Select("""
        SELECT
          u.user_id,
          u.gender,
          u.age_group,
          p.city,
          CAST(p.interest_tags AS CHAR) AS interest_tags_json,
          p.travel_style,
          p.physical_level,
          p.companion_type,
          CAST(p.food_preference AS CHAR) AS food_preference_json,
          CAST(p.accessibility_need AS CHAR) AS accessibility_need_json,
          p.source_type,
          p.ai_sync_status,
          p.travel_style AS travel_preference,
          p.budget_level AS consumption_level,
          u.profile_completed
        FROM tourist_user u
          LEFT JOIN tourist_profile p ON p.user_id = u.user_id
        WHERE u.user_id = #{userId}
          AND u.deleted = 0
        LIMIT 1
    """)
    @Results(id = "TouristProfileInfoMap", value = {
            @Result(column = "user_id", property = "userId"),
            @Result(column = "gender", property = "gender"),
            @Result(column = "age_group", property = "ageGroup"),
            @Result(column = "city", property = "city"),
            @Result(column = "interest_tags_json", property = "interestTagsJson"),
            @Result(column = "travel_style", property = "travelStyle"),
            @Result(column = "physical_level", property = "physicalLevel"),
            @Result(column = "companion_type", property = "companionType"),
            @Result(column = "food_preference_json", property = "foodPreferenceJson"),
            @Result(column = "accessibility_need_json", property = "accessibilityNeedJson"),
            @Result(column = "source_type", property = "sourceType"),
            @Result(column = "ai_sync_status", property = "aiSyncStatus"),
            @Result(column = "travel_preference", property = "travelPreference"),
            @Result(column = "consumption_level", property = "consumptionLevel"),
            @Result(column = "profile_completed", property = "profileCompleted")
    })
    TouristProfileInfo selectProfileInfo(@Param("userId") String userId);

    @Select("""
        SELECT COUNT(1)
        FROM tourist_user
        WHERE user_id = #{userId}
          AND deleted = 0
    """)
    int countUserByUserId(@Param("userId") String userId);

    @Select("""
        SELECT COUNT(1)
        FROM tourist_profile
        WHERE user_id = #{userId}
    """)
    int countProfileByUserId(@Param("userId") String userId);

    @Insert("""
        INSERT INTO tourist_profile (
          user_id,
          city,
          interest_tags,
          travel_style,
          physical_level,
          companion_type,
          food_preference,
          accessibility_need,
          source_type,
          ai_sync_status,
          created_at,
          updated_at
        ) VALUES (
          #{userId},
          #{city},
          CAST(#{interestTagsJson} AS JSON),
          #{travelStyle},
          #{physicalLevel},
          #{companionType},
          CAST(#{foodPreferenceJson} AS JSON),
          CAST(#{accessibilityNeedJson} AS JSON),
          'MANUAL',
          'PENDING',
          NOW(),
          NOW()
        )
    """)
    int insertProfile(
            @Param("userId") String userId,
            @Param("city") String city,
            @Param("interestTagsJson") String interestTagsJson,
            @Param("travelStyle") String travelStyle,
            @Param("physicalLevel") String physicalLevel,
            @Param("companionType") String companionType,
            @Param("foodPreferenceJson") String foodPreferenceJson,
            @Param("accessibilityNeedJson") String accessibilityNeedJson
    );

    @Update("""
        UPDATE tourist_profile
        SET city = #{city},
            interest_tags = CAST(#{interestTagsJson} AS JSON),
            travel_style = #{travelStyle},
            physical_level = #{physicalLevel},
            companion_type = #{companionType},
            food_preference = CAST(#{foodPreferenceJson} AS JSON),
            accessibility_need = CAST(#{accessibilityNeedJson} AS JSON),
            source_type = 'MANUAL',
            ai_sync_status = 'PENDING',
            updated_at = NOW()
        WHERE user_id = #{userId}
    """)
    int updateProfile(
            @Param("userId") String userId,
            @Param("city") String city,
            @Param("interestTagsJson") String interestTagsJson,
            @Param("travelStyle") String travelStyle,
            @Param("physicalLevel") String physicalLevel,
            @Param("companionType") String companionType,
            @Param("foodPreferenceJson") String foodPreferenceJson,
            @Param("accessibilityNeedJson") String accessibilityNeedJson
    );

    @Update("""
        UPDATE tourist_user
        SET gender = #{gender},
            age_group = #{ageGroup},
            profile_completed = #{profileCompleted},
            updated_at = NOW()
        WHERE user_id = #{userId}
          AND deleted = 0
    """)
    int updateUserProfileFields(
            @Param("userId") String userId,
            @Param("gender") String gender,
            @Param("ageGroup") String ageGroup,
            @Param("profileCompleted") Integer profileCompleted
    );

    @Delete("""
        DELETE FROM user_profile_tag
        WHERE user_id = #{userId}
          AND dimension_code = #{dimensionCode}
          AND source = 'explicit'
    """)
    int deleteExplicitTags(
            @Param("userId") String userId,
            @Param("dimensionCode") String dimensionCode
    );

    @Insert("""
        INSERT INTO user_profile_tag (
          user_id,
          dimension_code,
          tag_code,
          tag_name,
          score,
          source,
          reason
        ) VALUES (
          #{userId},
          #{dimensionCode},
          #{tagCode},
          #{tagName},
          1.0000,
          'explicit',
          'user profile save'
        )
        ON DUPLICATE KEY UPDATE
          tag_name = VALUES(tag_name),
          score = VALUES(score),
          source = VALUES(source),
          reason = VALUES(reason),
          updated_at = NOW()
    """)
    int upsertExplicitTag(
            @Param("userId") String userId,
            @Param("dimensionCode") String dimensionCode,
            @Param("tagCode") String tagCode,
            @Param("tagName") String tagName
    );

    @Select("""
        SELECT
          dimension_code,
          tag_code,
          tag_name,
          score,
          source,
          reason
        FROM user_profile_tag
        WHERE user_id = #{userId}
        ORDER BY score DESC, updated_at DESC
        LIMIT 30
    """)
    @Results(id = "ProfileTagMap", value = {
            @Result(column = "dimension_code", property = "dimensionCode"),
            @Result(column = "tag_code", property = "tagCode"),
            @Result(column = "tag_name", property = "tagName"),
            @Result(column = "score", property = "score"),
            @Result(column = "source", property = "source"),
            @Result(column = "reason", property = "reason")
    })
    java.util.List<UserProfileAiContextResponse.ProfileTag> selectProfileTags(@Param("userId") String userId);

    @Select("""
        SELECT content
        FROM chat_message
        WHERE user_id = #{userId}
          AND role = 'user'
          AND content IS NOT NULL
          AND content != ''
        ORDER BY created_at DESC, id DESC
        LIMIT 5
    """)
    java.util.List<String> selectRecentQuestions(@Param("userId") String userId);

    @Select("""
        SELECT
          event_id AS eventId,
          event_type AS eventType,
          entity_type AS entityType,
          entity_id AS entityId,
          area_id AS areaId,
          spot_id AS spotId,
          content,
          event_time AS eventTime
        FROM tourist_behavior_event
        WHERE user_id = #{userId}
          AND (#{areaId} IS NULL OR area_id = #{areaId})
        ORDER BY event_time DESC, id DESC
        LIMIT 10
    """)
    java.util.List<java.util.Map<String, Object>> selectRecentBehaviors(
            @Param("userId") String userId,
            @Param("areaId") Long areaId
    );

    @Select("""
        SELECT
          CAST(COALESCE(r.spot_id, 0) AS CHAR) AS spotId,
          COALESCE(s.name, r.frontend_scenic_name) AS spotName
        FROM tourist_spot_visit_record r
          LEFT JOIN scenic_spot s ON s.id = r.spot_id
        WHERE r.user_id = #{userId}
          AND (#{areaId} IS NULL OR r.area_id = #{areaId})
        ORDER BY COALESCE(r.leave_time, r.enter_time) DESC, r.id DESC
        LIMIT 1
    """)
    java.util.Map<String, Object> selectLatestVisitedSpot(
            @Param("userId") String userId,
            @Param("areaId") Long areaId
    );

    @Select("""
        SELECT CAST(id AS CHAR)
        FROM tourist_route_plan
        WHERE user_id = #{userId}
          AND (#{areaId} IS NULL OR area_id = #{areaId})
        ORDER BY created_at DESC, id DESC
        LIMIT 1
    """)
    String selectLatestRoutePlanId(
            @Param("userId") String userId,
            @Param("areaId") Long areaId
    );
}
