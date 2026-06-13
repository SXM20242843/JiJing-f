package com.scenic.ai.modules.app.user.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface BehaviorEventMapper {

    @Select("""
        SELECT id
        FROM scenic_area
        WHERE area_code = #{areaCode}
          AND deleted = 0
        LIMIT 1
    """)
    Long selectAreaIdByAreaCode(@Param("areaCode") String areaCode);

    @Select("""
        SELECT id
        FROM scenic_spot
        WHERE scene_code = #{sceneCode}
          AND deleted = 0
        LIMIT 1
    """)
    Long selectSpotIdBySceneCode(@Param("sceneCode") String sceneCode);

    @Insert("""
        INSERT IGNORE INTO tourist_behavior_event (
          event_id,
          user_id,
          session_id,
          visit_id,
          area_id,
          spot_id,
          facility_id,
          entity_type,
          entity_id,
          event_type,
          event_name,
          source_page,
          keyword,
          content,
          score,
          duration_seconds,
          longitude,
          latitude,
          gps_accuracy_m,
          device_id,
          client_type,
          extra_json,
          event_time
        ) VALUES (
          #{eventId},
          #{userId},
          #{sessionId},
          #{visitId},
          #{areaId},
          #{spotId},
          #{facilityId},
          #{entityType},
          #{entityId},
          #{eventType},
          #{eventName},
          #{sourcePage},
          #{keyword},
          #{content},
          #{score},
          #{durationSeconds},
          #{longitude},
          #{latitude},
          #{gpsAccuracyM},
          #{deviceId},
          #{clientType},
          CAST(#{extraJson} AS JSON),
          NOW()
        )
    """)
    int insertBehaviorEvent(
            @Param("eventId") String eventId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("visitId") Long visitId,
            @Param("areaId") Long areaId,
            @Param("spotId") Long spotId,
            @Param("facilityId") Long facilityId,
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("eventType") String eventType,
            @Param("eventName") String eventName,
            @Param("sourcePage") String sourcePage,
            @Param("keyword") String keyword,
            @Param("content") String content,
            @Param("score") java.math.BigDecimal score,
            @Param("durationSeconds") Integer durationSeconds,
            @Param("longitude") java.math.BigDecimal longitude,
            @Param("latitude") java.math.BigDecimal latitude,
            @Param("gpsAccuracyM") java.math.BigDecimal gpsAccuracyM,
            @Param("deviceId") String deviceId,
            @Param("clientType") String clientType,
            @Param("extraJson") String extraJson
    );
}
