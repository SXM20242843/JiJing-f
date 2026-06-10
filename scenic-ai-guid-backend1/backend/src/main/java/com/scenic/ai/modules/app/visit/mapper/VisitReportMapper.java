package com.scenic.ai.modules.app.visit.mapper;

import com.scenic.ai.modules.app.visit.dto.BehaviorStatsDto;
import com.scenic.ai.modules.app.visit.dto.RecommendParkDto;
import com.scenic.ai.modules.app.visit.dto.SatisfactionInfoDto;
import com.scenic.ai.modules.app.visit.dto.VisitReportResponse;
import com.scenic.ai.modules.app.visit.dto.VisitReportSpotDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VisitReportMapper {

    @Select("""
        SELECT
          v.id AS visit_id,
          v.visit_no,
          v.user_id,
          v.area_id,
          a.area_code AS park_id,
          a.area_name AS park_name,
          a.intro AS park_desc,
          a.image_url AS cover_image,
          v.group_size,
          v.travel_type,
          v.visit_preference,
          v.start_time,
          v.end_time,
          v.total_duration_seconds AS stay_duration,
          v.visit_status,
          v.consume_status,
          v.ticket_cost,
          v.food_cost,
          v.shopping_cost,
          v.transport_cost,
          v.entertainment_cost,
          v.total_cost,
          v.satisfaction AS session_satisfaction,
          v.`comment` AS session_comment,
          v.recommend
        FROM tourist_visit_session v
          LEFT JOIN scenic_area a ON a.id = v.area_id
        WHERE v.id = #{visitId}
        LIMIT 1
    """)
    @Results(id = "VisitReportMap", value = {
            @Result(column = "visit_id", property = "visitId"),
            @Result(column = "visit_no", property = "visitNo"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "area_id", property = "areaId"),
            @Result(column = "park_id", property = "parkId"),
            @Result(column = "park_name", property = "parkName"),
            @Result(column = "park_desc", property = "parkDesc"),
            @Result(column = "cover_image", property = "coverImage"),
            @Result(column = "group_size", property = "groupSize"),
            @Result(column = "travel_type", property = "travelType"),
            @Result(column = "visit_preference", property = "visitPreference"),
            @Result(column = "start_time", property = "rawStartTime"),
            @Result(column = "end_time", property = "rawEndTime"),
            @Result(column = "stay_duration", property = "stayDuration"),
            @Result(column = "visit_status", property = "visitStatus"),
            @Result(column = "consume_status", property = "consumeStatus"),
            @Result(column = "ticket_cost", property = "ticketCost"),
            @Result(column = "food_cost", property = "foodCost"),
            @Result(column = "shopping_cost", property = "shoppingCost"),
            @Result(column = "transport_cost", property = "transportCost"),
            @Result(column = "entertainment_cost", property = "entertainmentCost"),
            @Result(column = "total_cost", property = "totalCost"),
            @Result(column = "session_satisfaction", property = "sessionSatisfaction"),
            @Result(column = "session_comment", property = "sessionComment"),
            @Result(column = "recommend", property = "recommend")
    })
    VisitReportResponse selectVisitReportBase(@Param("visitId") Long visitId);

    @Select("""
        SELECT
          COALESCE(CAST(r.spot_id AS CHAR), NULLIF(r.frontend_scenic_id, ''), NULLIF(s.scene_code, '')) AS spot_id,
          COALESCE(NULLIF(r.frontend_scenic_id, ''), NULLIF(s.scene_code, ''), CAST(r.spot_id AS CHAR)) AS scenic_id,
          COALESCE(NULLIF(r.frontend_scenic_name, ''), NULLIF(s.name, ''), '未知景点') AS scenic_name,
          r.enter_time,
          r.leave_time,
          r.stay_seconds
        FROM tourist_spot_visit_record r
          LEFT JOIN scenic_spot s ON s.id = r.spot_id
        WHERE r.visit_id = #{visitId}
        ORDER BY r.enter_time ASC, r.id ASC
    """)
    @Results(id = "VisitReportSpotMap", value = {
            @Result(column = "spot_id", property = "spotId"),
            @Result(column = "scenic_id", property = "scenicId"),
            @Result(column = "scenic_name", property = "scenicName"),
            @Result(column = "enter_time", property = "rawEnterTime"),
            @Result(column = "leave_time", property = "rawLeaveTime"),
            @Result(column = "stay_seconds", property = "staySeconds")
    })
    List<VisitReportSpotDto> selectVisitReportSpots(@Param("visitId") Long visitId);

    @Select("""
        SELECT COUNT(1)
        FROM chat_message m
          INNER JOIN tourist_visit_session v ON v.id = #{visitId}
        WHERE m.user_id = v.user_id
          AND m.role = 'user'
          AND (
            (v.chat_session_id IS NOT NULL AND v.chat_session_id != '' AND m.session_id = v.chat_session_id)
            OR (
              m.area_id = v.area_id
              AND m.created_at >= v.start_time
              AND m.created_at <= COALESCE(v.end_time, NOW())
            )
          )
    """)
    Integer selectQuestionCountForVisit(@Param("visitId") Long visitId);

    @Select("""
        SELECT COUNT(1)
        FROM chat_message
        WHERE visit_id = #{visitId}
          AND role = 'user'
    """)
    Integer selectQuestionCountByMessageVisitId(@Param("visitId") Long visitId);

    @Select("""
        SELECT COUNT(1)
        FROM chat_message m
          INNER JOIN chat_session s ON s.session_id = m.session_id
        WHERE s.visit_id = #{visitId}
          AND m.role = 'user'
    """)
    Integer selectQuestionCountByChatSessionVisitId(@Param("visitId") Long visitId);

    @Select("""
        SELECT COUNT(1)
        FROM tourist_favorite f
          INNER JOIN tourist_visit_session v ON v.id = #{visitId}
          LEFT JOIN scenic_spot s ON f.target_type = 'SPOT' AND s.id = f.target_id
        WHERE f.user_id = v.user_id
          AND f.status = 1
          AND f.created_at >= v.start_time
          AND f.created_at <= COALESCE(v.end_time, NOW())
          AND (
            (f.target_type = 'AREA' AND f.target_id = v.area_id)
            OR (f.target_type = 'SPOT' AND s.area_id = v.area_id)
            OR f.target_type IN ('ROUTE', 'FACILITY')
          )
    """)
    Integer selectFavoriteCountForVisit(@Param("visitId") Long visitId);

    @Select("""
        SELECT COUNT(1)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = #{tableName}
          AND COLUMN_NAME = #{columnName}
    """)
    Integer countTableColumn(
            @Param("tableName") String tableName,
            @Param("columnName") String columnName
    );

    @Select("""
        SELECT JSON_UNQUOTE(JSON_EXTRACT(extra_json, CONCAT('$.', #{jsonKey})))
        FROM tourist_behavior_event
        WHERE visit_id = #{visitId}
          AND LOWER(event_type) IN ('visit_start', 'guide_start')
        ORDER BY event_time DESC, id DESC
        LIMIT 1
    """)
    String selectVisitStartExtraValue(
            @Param("visitId") Long visitId,
            @Param("jsonKey") String jsonKey
    );

    @Select("""
        SELECT
          COALESCE(SUM(CASE WHEN UPPER(event_type) IN ('ASK', 'VOICE_INPUT', 'AI_QUESTION', 'ASK_AI_QUESTION') THEN 1 ELSE 0 END), 0) AS ai_question_count,
          COALESCE(SUM(CASE WHEN UPPER(event_type) LIKE '%FAVORITE%' THEN 1 ELSE 0 END), 0) AS favorite_count
        FROM tourist_behavior_event
        WHERE visit_id = #{visitId}
    """)
    @Results(id = "BehaviorStatsMap", value = {
            @Result(column = "ai_question_count", property = "aiQuestionCount"),
            @Result(column = "favorite_count", property = "favoriteCount")
    })
    BehaviorStatsDto selectBehaviorStats(@Param("visitId") Long visitId);

    @Select("""
        SELECT
          score AS satisfaction,
          comment
        FROM tourist_satisfaction_record
        WHERE visit_id = #{visitId}
        ORDER BY created_at DESC, id DESC
        LIMIT 1
    """)
    @Results(id = "SatisfactionInfoMap", value = {
            @Result(column = "satisfaction", property = "satisfaction"),
            @Result(column = "comment", property = "comment")
    })
    SatisfactionInfoDto selectSatisfactionInfo(@Param("visitId") Long visitId);

    @Select("""
        SELECT
          a.area_code AS park_id,
          a.area_name AS park_name,
          a.intro AS park_desc,
          a.image_url AS cover_image
        FROM scenic_area a
        WHERE a.deleted = 0
          AND a.status = 1
          AND a.id <> #{areaId}
          AND a.park_type = (
            SELECT park_type
            FROM scenic_area
            WHERE id = #{areaId}
            LIMIT 1
          )
        ORDER BY a.updated_at DESC
        LIMIT 3
    """)
    @Results(id = "RecommendParkMap", value = {
            @Result(column = "park_id", property = "parkId"),
            @Result(column = "park_name", property = "parkName"),
            @Result(column = "park_desc", property = "parkDesc"),
            @Result(column = "cover_image", property = "coverImage")
    })
    List<RecommendParkDto> selectRecommendParks(@Param("areaId") Long areaId);
}
