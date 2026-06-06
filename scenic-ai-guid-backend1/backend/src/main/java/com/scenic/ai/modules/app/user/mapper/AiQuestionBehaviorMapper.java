package com.scenic.ai.modules.app.user.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface AiQuestionBehaviorMapper {

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
        INSERT INTO tourist_behavior_event (
          event_id,
          user_id,
          session_id,
          visit_id,
          area_id,
          spot_id,
          entity_type,
          entity_id,
          event_type,
          event_name,
          source_page,
          content,
          longitude,
          latitude,
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
          #{entityType},
          #{entityId},
          #{eventType},
          'AI数字人提问',
          'native-live2d-guide',
          #{content},
          #{longitude},
          #{latitude},
          'APP',
          CAST(#{extraJson} AS JSON),
          NOW()
        )
    """)
    int insertAiQuestionEvent(
            @Param("eventId") String eventId,
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("visitId") Long visitId,
            @Param("areaId") Long areaId,
            @Param("spotId") Long spotId,
            @Param("entityType") String entityType,
            @Param("entityId") String entityId,
            @Param("eventType") String eventType,
            @Param("content") String content,
            @Param("longitude") BigDecimal longitude,
            @Param("latitude") BigDecimal latitude,
            @Param("extraJson") String extraJson
    );
}
