package com.scenic.ai.modules.chat.mapper;

import com.scenic.ai.modules.chat.entity.ChatMessage;
import com.scenic.ai.modules.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatPersistenceMapper {

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

    @Select("""
        SELECT id
        FROM scenic_spot
        WHERE id = #{spotId}
          AND deleted = 0
        LIMIT 1
    """)
    Long selectSpotIdById(@Param("spotId") Long spotId);

    @Insert("""
        INSERT INTO chat_session (
          session_id,
          user_id,
          session_name,
          scene_id,
          status,
          area_code,
          area_name,
          current_spot_id,
          current_spot_name,
          user_name,
          area_id
        ) VALUES (
          #{session.sessionId},
          #{session.userId},
          #{session.sessionName},
          #{session.sceneId},
          #{session.status},
          #{session.areaCode},
          #{session.areaName},
          #{session.currentSpotId},
          #{session.currentSpotName},
          #{session.userName},
          #{session.areaId}
        )
        ON DUPLICATE KEY UPDATE
          user_id = VALUES(user_id),
          session_name = COALESCE(VALUES(session_name), session_name),
          scene_id = COALESCE(VALUES(scene_id), scene_id),
          status = 1,
          area_code = COALESCE(VALUES(area_code), area_code),
          area_name = COALESCE(VALUES(area_name), area_name),
          current_spot_id = COALESCE(VALUES(current_spot_id), current_spot_id),
          current_spot_name = COALESCE(VALUES(current_spot_name), current_spot_name),
          user_name = COALESCE(VALUES(user_name), user_name),
          area_id = COALESCE(VALUES(area_id), area_id),
          updated_at = NOW()
    """)
    int upsertChatSession(@Param("session") ChatSession session);

    @Insert("""
        INSERT INTO chat_message (
          session_id,
          user_id,
          role,
          content,
          scene_id,
          source_type,
          source_ref,
          message_id,
          rewritten_question,
          intent,
          input_type,
          area_code,
          area_name,
          current_spot_id,
          current_spot_name,
          audio_url,
          audio_format,
          tts_error,
          current_entity,
          sources,
          area_id,
          intent_type
        ) VALUES (
          #{message.sessionId},
          #{message.userId},
          #{message.role},
          #{message.content},
          #{message.sceneId},
          #{message.sourceType},
          #{message.sourceRef},
          #{message.messageId},
          #{message.rewrittenQuestion},
          #{message.intent},
          #{message.inputType},
          #{message.areaCode},
          #{message.areaName},
          #{message.currentSpotId},
          #{message.currentSpotName},
          #{message.audioUrl},
          #{message.audioFormat},
          #{message.ttsError},
          #{message.currentEntity},
          #{message.sources},
          #{message.areaId},
          #{message.intentType}
        )
    """)
    int insertChatMessage(@Param("message") ChatMessage message);
}
