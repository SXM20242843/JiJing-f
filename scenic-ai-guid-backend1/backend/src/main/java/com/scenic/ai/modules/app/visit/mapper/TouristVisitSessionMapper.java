package com.scenic.ai.modules.app.visit.mapper;

import com.scenic.ai.modules.app.visit.entity.TouristVisitSession;
import com.scenic.ai.modules.app.visit.dto.VisitStatusResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TouristVisitSessionMapper {

    @Select("""
        SELECT
          id,
          visit_no,
          user_id,
          chat_session_id,
          area_id,
          start_time,
          end_time,
          total_duration_seconds,
          visit_status,
          group_size,
          travel_type,
          visit_preference
        FROM tourist_visit_session
        WHERE id = #{visitId}
        LIMIT 1
    """)
    @Results(id = "VisitEndSessionMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "visit_no", property = "visitNo"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "chat_session_id", property = "chatSessionId"),
            @Result(column = "area_id", property = "areaId"),
            @Result(column = "start_time", property = "startTime"),
            @Result(column = "end_time", property = "endTime"),
            @Result(column = "total_duration_seconds", property = "totalDurationSeconds"),
            @Result(column = "visit_status", property = "visitStatus"),
            @Result(column = "group_size", property = "groupSize"),
            @Result(column = "travel_type", property = "travelType"),
            @Result(column = "visit_preference", property = "visitPreference")
    })
    TouristVisitSession selectVisitSessionForEnd(@Param("visitId") Long visitId);

    @Select("""
        SELECT
          CAST(v.id AS CHAR) AS visit_id,
          v.user_id,
          CAST(v.area_id AS CHAR) AS area_id,
          a.area_name,
          v.start_time,
          v.end_time,
          CASE
            WHEN v.end_time IS NULL THEN TIMESTAMPDIFF(SECOND, v.start_time, NOW())
            ELSE COALESCE(v.total_duration_seconds, TIMESTAMPDIFF(SECOND, v.start_time, v.end_time))
          END AS duration_seconds,
          v.visit_status
        FROM tourist_visit_session v
          LEFT JOIN scenic_area a ON a.id = v.area_id
        WHERE v.id = #{visitId}
        LIMIT 1
    """)
    @Results(id = "VisitStatusMap", value = {
            @Result(column = "visit_id", property = "visitId"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "area_id", property = "areaId"),
            @Result(column = "area_name", property = "areaName"),
            @Result(column = "start_time", property = "rawStartTime"),
            @Result(column = "end_time", property = "rawEndTime"),
            @Result(column = "duration_seconds", property = "durationSeconds"),
            @Result(column = "visit_status", property = "rawStatus")
    })
    VisitStatusResponse selectVisitStatusById(@Param("visitId") Long visitId);

    @Select("""
        <script>
        SELECT
          CAST(v.id AS CHAR) AS visit_id,
          v.user_id,
          CAST(v.area_id AS CHAR) AS area_id,
          a.area_name,
          v.start_time,
          v.end_time,
          CASE
            WHEN v.end_time IS NULL THEN TIMESTAMPDIFF(SECOND, v.start_time, NOW())
            ELSE COALESCE(v.total_duration_seconds, TIMESTAMPDIFF(SECOND, v.start_time, v.end_time))
          END AS duration_seconds,
          v.visit_status
        FROM tourist_visit_session v
          LEFT JOIN scenic_area a ON a.id = v.area_id
        WHERE v.user_id = #{userId}
        <if test="areaId != null">
          AND v.area_id = #{areaId}
        </if>
        ORDER BY
          CASE
            WHEN UPPER(v.visit_status) IN ('VISITING', 'ACTIVE', 'IN_PROGRESS', 'ONGOING')
              AND v.end_time IS NULL THEN 0
            ELSE 1
          END ASC,
          v.start_time DESC,
          v.id DESC
        LIMIT 1
        </script>
    """)
    @ResultMap("VisitStatusMap")
    VisitStatusResponse selectLatestVisitStatus(
            @Param("userId") String userId,
            @Param("areaId") Long areaId
    );

    @Select("""
        <script>
        SELECT
          CAST(v.id AS CHAR) AS visit_id,
          v.user_id,
          CAST(v.area_id AS CHAR) AS area_id,
          a.area_name,
          v.start_time,
          v.end_time,
          CASE
            WHEN v.end_time IS NULL THEN TIMESTAMPDIFF(SECOND, v.start_time, NOW())
            ELSE COALESCE(v.total_duration_seconds, TIMESTAMPDIFF(SECOND, v.start_time, v.end_time))
          END AS duration_seconds,
          v.visit_status
        FROM tourist_visit_session v
          LEFT JOIN scenic_area a ON a.id = v.area_id
        WHERE v.user_id = #{userId}
          AND UPPER(v.visit_status) IN ('VISITING', 'ACTIVE', 'IN_PROGRESS', 'ONGOING')
          AND v.end_time IS NULL
        <if test="areaId != null">
          AND v.area_id = #{areaId}
        </if>
        ORDER BY v.start_time DESC, v.id DESC
        LIMIT 1
        </script>
    """)
    @ResultMap("VisitStatusMap")
    VisitStatusResponse selectRunningVisitStatus(
            @Param("userId") String userId,
            @Param("areaId") Long areaId
    );

    @Select("""
        <script>
        SELECT
          CAST(v.id AS CHAR) AS visit_id,
          v.user_id,
          CAST(v.area_id AS CHAR) AS area_id,
          a.area_name,
          v.start_time,
          v.end_time,
          COALESCE(v.total_duration_seconds, TIMESTAMPDIFF(SECOND, v.start_time, v.end_time)) AS duration_seconds,
          v.visit_status
        FROM tourist_visit_session v
          LEFT JOIN scenic_area a ON a.id = v.area_id
        WHERE v.user_id = #{userId}
          AND v.end_time IS NOT NULL
          AND UPPER(v.visit_status) IN ('FINISHED', 'FINISH', 'ENDED', 'COMPLETED')
        <if test="areaId != null">
          AND v.area_id = #{areaId}
        </if>
        ORDER BY v.end_time DESC, v.id DESC
        LIMIT 1
        </script>
    """)
    @ResultMap("VisitStatusMap")
    VisitStatusResponse selectLastCompletedVisitStatus(
            @Param("userId") String userId,
            @Param("areaId") Long areaId
    );

    @Select("""
    SELECT id, visit_no, user_id, area_id, start_time, visit_status, group_size, travel_type, visit_preference
    FROM tourist_visit_session
    WHERE user_id = #{userId}
      AND area_id = (SELECT id FROM scenic_area WHERE area_code = #{parkId} LIMIT 1)
      AND UPPER(visit_status) IN ('VISITING', 'ACTIVE', 'IN_PROGRESS', 'ONGOING')
      AND end_time IS NULL
    ORDER BY start_time DESC
    LIMIT 1
""")
    TouristVisitSession selectActiveSessionByUserAndArea(@Param("userId") String userId, @Param("parkId") String parkId);

    @Select("""
        SELECT
          id,
          visit_no,
          user_id,
          chat_session_id,
          area_id,
          start_time,
          end_time,
          total_duration_seconds,
          visit_status,
          group_size,
          travel_type,
          visit_preference
        FROM tourist_visit_session
        WHERE user_id = #{userId}
          AND area_id = #{areaId}
          AND UPPER(visit_status) IN ('VISITING', 'ACTIVE', 'IN_PROGRESS', 'ONGOING')
          AND end_time IS NULL
        ORDER BY start_time DESC, id DESC
        LIMIT 1
    """)
    @ResultMap("VisitEndSessionMap")
    TouristVisitSession selectActiveSessionByUserAndAreaId(
            @Param("userId") String userId,
            @Param("areaId") Long areaId
    );

    @Select("""
        SELECT id
        FROM scenic_area
        WHERE area_code = #{parkId}
          AND deleted = 0
        LIMIT 1
    """)
    Long selectAreaIdByAreaCode(@Param("parkId") String parkId);

    @Select("""
        SELECT id
        FROM scenic_area
        WHERE area_name = #{parkName}
          AND deleted = 0
        LIMIT 1
    """)
    Long selectAreaIdByAreaName(@Param("parkName") String parkName);

    @Select("""
        SELECT area_name
        FROM scenic_area
        WHERE id = #{areaId}
          AND deleted = 0
        LIMIT 1
    """)
    String selectAreaNameById(@Param("areaId") Long areaId);

    @Select("""
        SELECT area_code
        FROM scenic_area
        WHERE id = #{areaId}
          AND deleted = 0
        LIMIT 1
    """)
    String selectAreaCodeById(@Param("areaId") Long areaId);

    @Insert("""
        INSERT INTO tourist_visit_session (
          visit_no,
          user_id,
          chat_session_id,
          area_id,
          start_time,
          start_longitude,
          start_latitude,
          visit_status,
          source_type,
          group_size,
          travel_type,
          visit_preference,
          consume_status,
          ticket_cost,
          food_cost,
          shopping_cost,
          transport_cost,
          entertainment_cost,
          total_cost,
          created_at,
          updated_at
        ) VALUES (
          #{visitNo},
          #{userId},
          #{chatSessionId},
          #{areaId},
          #{startTime},
          #{startLongitude},
          #{startLatitude},
          #{visitStatus},
          #{sourceType},
          #{groupSize},
          #{travelType},
          #{visitPreference},
          #{consumeStatus},
          #{ticketCost},
          #{foodCost},
          #{shoppingCost},
          #{transportCost},
          #{entertainmentCost},
          #{totalCost},
          #{createdAt},
          #{updatedAt}
        )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertVisitSession(TouristVisitSession session);

    @Update("""
        UPDATE tourist_visit_session
        SET start_longitude = COALESCE(#{startLongitude}, start_longitude),
            start_latitude = COALESCE(#{startLatitude}, start_latitude),
            source_type = COALESCE(#{sourceType}, source_type),
            group_size = COALESCE(#{groupSize}, group_size),
            travel_type = COALESCE(#{travelType}, travel_type),
            visit_preference = COALESCE(#{visitPreference}, visit_preference),
            updated_at = #{updatedAt}
        WHERE id = #{visitId}
          AND UPPER(visit_status) IN ('VISITING', 'ACTIVE', 'IN_PROGRESS', 'ONGOING')
          AND end_time IS NULL
    """)
    int updateVisitStartSnapshot(
            @Param("visitId") Long visitId,
            @Param("startLongitude") java.math.BigDecimal startLongitude,
            @Param("startLatitude") java.math.BigDecimal startLatitude,
            @Param("sourceType") String sourceType,
            @Param("groupSize") String groupSize,
            @Param("travelType") String travelType,
            @Param("visitPreference") String visitPreference,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Update("""
        UPDATE tourist_visit_session
        SET chat_session_id = #{chatSessionId},
            updated_at = NOW()
        WHERE id = #{visitId}
          AND user_id = #{userId}
          AND (chat_session_id IS NULL OR chat_session_id = '')
    """)
    int bindChatSessionIdIfAbsent(
            @Param("visitId") Long visitId,
            @Param("userId") String userId,
            @Param("chatSessionId") String chatSessionId
    );

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

    @Update("""
        UPDATE tourist_visit_session
        SET end_time = #{endTime},
            end_longitude = #{endLongitude},
            end_latitude = #{endLatitude},
            total_duration_seconds = #{totalDurationSeconds},
            visit_status = 'COMPLETED',
            updated_at = #{endTime}
        WHERE id = #{visitId}
    """)
    int updateVisitEnd(
            @Param("visitId") Long visitId,
            @Param("endTime") LocalDateTime endTime,
            @Param("endLongitude") java.math.BigDecimal endLongitude,
            @Param("endLatitude") java.math.BigDecimal endLatitude,
            @Param("totalDurationSeconds") Integer totalDurationSeconds
    );

    @Update("""
        UPDATE tourist_visit_session
        SET end_reason = #{endReason},
            updated_at = NOW()
        WHERE id = #{visitId}
    """)
    int updateVisitEndReason(
            @Param("visitId") Long visitId,
            @Param("endReason") String endReason
    );
}
