package com.scenic.ai.modules.app.user.mapper;

import com.scenic.ai.modules.app.user.dto.ConsultHistoryItemDto;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatHistoryMapper {

    @Select("""
        SELECT
          u.id,
          COALESCE(u.message_id, CONCAT('msg_', u.id)) AS message_id,
          u.session_id,
          u.user_id,
          u.content AS question_text,

          (
            SELECT a.content
            FROM chat_message a
            WHERE a.session_id = u.session_id
              AND a.user_id = u.user_id
              AND a.role = 'assistant'
              AND a.created_at >= u.created_at
            ORDER BY a.created_at ASC, a.id ASC
            LIMIT 1
          ) AS answer_text,

          COALESCE(u.area_code, s.area_code) AS area_code,
          COALESCE(u.area_name, s.area_name) AS area_name,
          COALESCE(u.area_name, s.area_name) AS park_name,
          COALESCE(u.current_spot_id, s.current_spot_id) AS current_spot_id,
          COALESCE(u.current_spot_name, s.current_spot_name) AS current_spot_name,
          COALESCE(u.current_spot_name, s.current_spot_name) AS spot_name,

          COALESCE(u.input_type, 'text') AS input_type,
          COALESCE(u.source_type, 'native-live2d-guide') AS source,
          u.created_at
        FROM chat_message u
        LEFT JOIN chat_session s ON s.session_id = u.session_id
        WHERE u.user_id = #{userId}
          AND u.role = 'user'
        ORDER BY u.created_at DESC, u.id DESC
        LIMIT #{limit}
    """)
    @Results(id = "ConsultHistoryItemMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "message_id", property = "messageId"),
            @Result(column = "session_id", property = "sessionId"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "question_text", property = "questionText"),
            @Result(column = "answer_text", property = "answerText"),
            @Result(column = "area_code", property = "areaCode"),
            @Result(column = "area_name", property = "areaName"),
            @Result(column = "park_name", property = "parkName"),
            @Result(column = "current_spot_id", property = "currentSpotId"),
            @Result(column = "current_spot_name", property = "currentSpotName"),
            @Result(column = "spot_name", property = "spotName"),
            @Result(column = "input_type", property = "inputType"),
            @Result(column = "source", property = "source"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<ConsultHistoryItemDto> selectConsultHistory(
            @Param("userId") String userId,
            @Param("limit") int limit
    );
}