package com.scenic.ai.modules.app.visit.mapper;

import com.scenic.ai.modules.app.visit.entity.TouristVisitSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface VisitFeedbackMapper {

    @Select("""
        SELECT
          id,
          user_id,
          chat_session_id,
          area_id
        FROM tourist_visit_session
        WHERE id = #{visitId}
        LIMIT 1
    """)
    @Results(id = "VisitFeedbackSessionMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "chat_session_id", property = "chatSessionId"),
            @Result(column = "area_id", property = "areaId")
    })
    TouristVisitSession selectVisitSessionById(@Param("visitId") Long visitId);

    @Select("""
        SELECT id
        FROM tourist_satisfaction_record
        WHERE visit_id = #{visitId}
        ORDER BY created_at DESC, id DESC
        LIMIT 1
    """)
    Long selectLatestSatisfactionRecordId(@Param("visitId") Long visitId);

    @Insert("""
        INSERT INTO tourist_satisfaction_record (
          user_id,
          session_id,
          visit_id,
          area_id,
          rating_type,
          score,
          comment,
          created_at
        ) VALUES (
          #{userId},
          #{sessionId},
          #{visitId},
          #{areaId},
          'VISIT',
          #{score},
          #{comment},
          #{createdAt}
        )
    """)
    int insertSatisfactionRecord(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("visitId") Long visitId,
            @Param("areaId") Long areaId,
            @Param("score") BigDecimal score,
            @Param("comment") String comment,
            @Param("createdAt") LocalDateTime createdAt
    );

    @Update("""
        UPDATE tourist_satisfaction_record
        SET score = #{score},
            comment = #{comment}
        WHERE id = #{recordId}
    """)
    int updateSatisfactionRecord(
            @Param("recordId") Long recordId,
            @Param("score") BigDecimal score,
            @Param("comment") String comment
    );

    @Update("""
        UPDATE tourist_visit_session
        SET satisfaction = #{satisfaction},
            `comment` = #{comment},
            recommend = #{recommend},
            updated_at = #{updatedAt}
        WHERE id = #{visitId}
    """)
    int updateVisitSessionFeedback(
            @Param("visitId") Long visitId,
            @Param("satisfaction") Integer satisfaction,
            @Param("comment") String comment,
            @Param("recommend") Integer recommend,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
