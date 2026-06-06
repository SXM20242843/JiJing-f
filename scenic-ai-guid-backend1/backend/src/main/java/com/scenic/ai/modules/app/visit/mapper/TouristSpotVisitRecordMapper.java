package com.scenic.ai.modules.app.visit.mapper;

import com.scenic.ai.modules.app.visit.entity.TouristSpotVisitRecord;
import com.scenic.ai.modules.app.visit.entity.TouristVisitSession;
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
public interface TouristSpotVisitRecordMapper {

    @Select("""
        SELECT id, user_id, chat_session_id, area_id, end_time, visit_status
        FROM tourist_visit_session
        WHERE id = #{visitId}
        LIMIT 1
    """)
    @Results(id = "VisitSessionSimpleMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "chat_session_id", property = "chatSessionId"),
            @Result(column = "area_id", property = "areaId"),
            @Result(column = "end_time", property = "endTime"),
            @Result(column = "visit_status", property = "visitStatus")
    })
    TouristVisitSession selectVisitSessionById(@Param("visitId") Long visitId);

    @Select("""
        SELECT id
        FROM scenic_spot
        WHERE area_id = #{areaId}
          AND deleted = 0
          AND status = 1
          AND (
            scene_code = #{scenicId}
            OR spot_level_code = #{scenicId}
            OR map_poi_id = #{scenicId}
            OR CAST(id AS CHAR) = #{scenicId}
            OR name = #{scenicId}
          )
        LIMIT 1
    """)
    Long selectSpotIdByScenicId(
            @Param("areaId") Long areaId,
            @Param("scenicId") String scenicId
    );

    @Insert("""
        INSERT INTO tourist_spot_visit_record (
          user_id,
          visit_id,
          area_id,
          spot_id,
          frontend_scenic_id,
          frontend_scenic_name,
          enter_time,
          enter_longitude,
          enter_latitude,
          source_type,
          created_at,
          updated_at
        ) VALUES (
          #{userId},
          #{visitId},
          #{areaId},
          #{spotId},
          #{frontendScenicId},
          #{frontendScenicName},
          #{enterTime},
          #{enterLongitude},
          #{enterLatitude},
          #{sourceType},
          #{createdAt},
          #{updatedAt}
        )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSpotVisitRecord(TouristSpotVisitRecord record);

    @Select("""
        <script>
        SELECT
          id,
          user_id,
          visit_id,
          area_id,
          spot_id,
          frontend_scenic_id,
          frontend_scenic_name,
          enter_time,
          leave_time,
          stay_seconds
        FROM tourist_spot_visit_record
        WHERE visit_id = #{visitId}
          AND leave_time IS NULL
        <choose>
          <when test="spotId != null">
            AND (spot_id = #{spotId} OR frontend_scenic_id = #{scenicId})
          </when>
          <otherwise>
            AND frontend_scenic_id = #{scenicId}
          </otherwise>
        </choose>
        ORDER BY enter_time DESC, id DESC
        LIMIT 1
        </script>
    """)
    @Results(id = "SpotVisitRecordMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "visit_id", property = "visitId"),
            @Result(column = "area_id", property = "areaId"),
            @Result(column = "spot_id", property = "spotId"),
            @Result(column = "frontend_scenic_id", property = "frontendScenicId"),
            @Result(column = "frontend_scenic_name", property = "frontendScenicName"),
            @Result(column = "enter_time", property = "enterTime"),
            @Result(column = "leave_time", property = "leaveTime"),
            @Result(column = "stay_seconds", property = "staySeconds")
    })
    TouristSpotVisitRecord selectOpenSpotVisitRecord(
            @Param("visitId") Long visitId,
            @Param("spotId") Long spotId,
            @Param("scenicId") String scenicId
    );

    @Select("""
        SELECT
          id,
          user_id,
          visit_id,
          area_id,
          spot_id,
          frontend_scenic_id,
          frontend_scenic_name,
          enter_time,
          leave_time,
          stay_seconds
        FROM tourist_spot_visit_record
        WHERE visit_id = #{visitId}
          AND leave_time IS NULL
        ORDER BY enter_time DESC, id DESC
        LIMIT 1
    """)
    @ResultMap("SpotVisitRecordMap")
    TouristSpotVisitRecord selectLatestOpenSpotVisitRecord(@Param("visitId") Long visitId);

    @Update("""
        UPDATE tourist_spot_visit_record
        SET leave_time = #{leaveTime},
            leave_longitude = #{leaveLongitude},
            leave_latitude = #{leaveLatitude},
            stay_seconds = #{staySeconds},
            updated_at = #{leaveTime}
        WHERE id = #{recordId}
    """)
    int updateSpotVisitLeave(
            @Param("recordId") Long recordId,
            @Param("leaveTime") LocalDateTime leaveTime,
            @Param("leaveLongitude") java.math.BigDecimal leaveLongitude,
            @Param("leaveLatitude") java.math.BigDecimal leaveLatitude,
            @Param("staySeconds") Integer staySeconds
    );

    @Insert("""
        INSERT INTO tourist_location_log (
          user_id,
          visit_id,
          area_id,
          spot_id,
          longitude,
          latitude,
          source_type,
          log_time,
          created_at
        ) VALUES (
          #{userId},
          #{visitId},
          #{areaId},
          #{spotId},
          #{longitude},
          #{latitude},
          #{sourceType},
          #{logTime},
          #{logTime}
        )
    """)
    int insertLocationLog(
            @Param("userId") String userId,
            @Param("visitId") Long visitId,
            @Param("areaId") Long areaId,
            @Param("spotId") Long spotId,
            @Param("longitude") java.math.BigDecimal longitude,
            @Param("latitude") java.math.BigDecimal latitude,
            @Param("sourceType") String sourceType,
            @Param("logTime") LocalDateTime logTime
    );
}
