package com.scenic.ai.modules.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenic.ai.modules.chat.dto.GuideChatRequest;
import com.scenic.ai.modules.chat.dto.GuideChatResponse;
import com.scenic.ai.modules.chat.dto.GuideVoiceChatResponse;
import com.scenic.ai.modules.chat.entity.ChatMessage;
import com.scenic.ai.modules.chat.entity.ChatSession;
import com.scenic.ai.modules.chat.mapper.ChatPersistenceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class ChatPersistenceService {

    private final ChatPersistenceMapper chatPersistenceMapper;
    private final ObjectMapper objectMapper;

    public ChatPersistenceService(ChatPersistenceMapper chatPersistenceMapper, ObjectMapper objectMapper) {
        this.chatPersistenceMapper = chatPersistenceMapper;
        this.objectMapper = objectMapper;
    }

    public String ensureTextSessionId(GuideChatRequest request) {
        String sessionId = firstNotBlank(request.getSessionId(), request.getConversationId());
        if (sessionId.isEmpty()) {
            sessionId = generateSessionId();
        }

        request.setSessionId(sessionId);
        if (!hasText(request.getConversationId())) {
            request.setConversationId(sessionId);
        }
        return sessionId;
    }

    public String ensureSessionId(String sessionId, String conversationId) {
        String value = firstNotBlank(sessionId, conversationId);
        return value.isEmpty() ? generateSessionId() : value;
    }

    public void saveTextExchangeSafely(GuideChatRequest request, GuideChatResponse response, String userId) {
        try {
            String sessionId = ensureTextSessionId(request);
            if (response != null && !hasText(response.getConversationId())) {
                response.setConversationId(sessionId);
            }

            Long areaId = resolveAreaId(request.getAreaId(), request.getEffectiveParkId());
            Long visitId = parseLongOrNull(request.getVisitId());
            Long sceneId = resolveSceneId(request.getEffectiveCurrentSpotId());
            String areaCode = nullIfBlank(request.getEffectiveParkId());
            String areaName = nullIfBlank(request.getEffectiveParkName());
            String currentSpotId = nullIfBlank(request.getEffectiveCurrentSpotId());
            String currentSpotName = nullIfBlank(request.getEffectiveCurrentSpotName());

            upsertSession(
                    sessionId,
                    userId,
                    buildSessionName(request.getQuestion()),
                    sceneId,
                    areaCode,
                    areaName,
                    currentSpotId,
                    currentSpotName,
                    areaId
            );

            insertUserMessage(
                    sessionId,
                    userId,
                    firstNotBlank(request.getQuestion(), ""),
                    sceneId,
                    nullIfBlank(request.getEffectiveInputType()),
                    areaCode,
                    areaName,
                    currentSpotId,
                    currentSpotName,
                    areaId
            );

            if (response != null) {
                insertAssistantMessage(
                        sessionId,
                        userId,
                        firstNotBlank(response.getAnswer(), "AI 暂无回复"),
                        sceneId,
                        areaCode,
                        areaName,
                        currentSpotId,
                        currentSpotName,
                        areaId,
                        response.getMessageId(),
                        response.getRewrittenQuestion(),
                        response.getIntent(),
                        request.getEffectiveInputType(),
                        response.getAudioUrl(),
                        response.getAudioFormat(),
                        response.getTtsError(),
                        writeJson(response.getCurrentEntity()),
                        writeJson(response.getSources())
                );
            }

            bindVisitIdSafely(sessionId, userId, visitId);
        } catch (Exception e) {
            log.warn("保存文本聊天记录失败，不影响AI响应。userId={}", userId, e);
        }
    }

    public void saveVoiceExchangeSafely(
            String sessionId,
            String userId,
            Long areaId,
            String areaCode,
            String areaName,
            String currentSpotId,
            String currentSpotName,
            String inputType,
            GuideVoiceChatResponse response
    ) {
        try {
            String finalSessionId = ensureSessionId(sessionId, response == null ? null : response.getConversationId());
            if (response != null && !hasText(response.getConversationId())) {
                response.setConversationId(finalSessionId);
            }

            Long resolvedAreaId = resolveAreaId(areaId, areaCode);
            Long sceneId = resolveSceneId(currentSpotId);

            String questionText = response == null ? "" : firstNotBlank(response.getQuestionText(), "语音输入");
            String answer = response == null ? "AI 暂无回复" : firstNotBlank(response.getAnswer(), "AI 暂无回复");

            upsertSession(
                    finalSessionId,
                    userId,
                    buildSessionName(questionText),
                    sceneId,
                    nullIfBlank(areaCode),
                    nullIfBlank(areaName),
                    nullIfBlank(currentSpotId),
                    nullIfBlank(currentSpotName),
                    resolvedAreaId
            );

            insertUserMessage(
                    finalSessionId,
                    userId,
                    questionText,
                    sceneId,
                    firstNotBlank(inputType, "voice"),
                    nullIfBlank(areaCode),
                    nullIfBlank(areaName),
                    nullIfBlank(currentSpotId),
                    nullIfBlank(currentSpotName),
                    resolvedAreaId
            );

            if (response != null) {
                insertAssistantMessage(
                        finalSessionId,
                        userId,
                        answer,
                        sceneId,
                        nullIfBlank(areaCode),
                        nullIfBlank(areaName),
                        nullIfBlank(currentSpotId),
                        nullIfBlank(currentSpotName),
                        resolvedAreaId,
                        response.getMessageId(),
                        response.getRewrittenQuestion(),
                        response.getIntent(),
                        firstNotBlank(inputType, "voice"),
                        response.getAudioUrl(),
                        response.getAudioFormat(),
                        response.getTtsError(),
                        writeJson(response.getCurrentEntity()),
                        writeJson(response.getSources())
                );
            }
        } catch (Exception e) {
            log.warn("保存语音聊天记录失败，不影响AI响应。userId={}", userId, e);
        }
    }

    private void upsertSession(
            String sessionId,
            String userId,
            String sessionName,
            Long sceneId,
            String areaCode,
            String areaName,
            String currentSpotId,
            String currentSpotName,
            Long areaId
    ) {
        ChatSession session = new ChatSession();
        session.sessionId = sessionId;
        session.userId = userId;
        session.sessionName = sessionName;
        session.sceneId = sceneId;
        session.status = 1;
        session.areaCode = areaCode;
        session.areaName = areaName;
        session.currentSpotId = currentSpotId;
        session.currentSpotName = currentSpotName;
        session.areaId = areaId;
        chatPersistenceMapper.upsertChatSession(session);
    }

    private void insertUserMessage(
            String sessionId,
            String userId,
            String content,
            Long sceneId,
            String inputType,
            String areaCode,
            String areaName,
            String currentSpotId,
            String currentSpotName,
            Long areaId
    ) {
        ChatMessage message = baseMessage(sessionId, userId, "user", content, sceneId, areaCode, areaName,
                currentSpotId, currentSpotName, areaId);
        message.sourceType = "manual";
        message.inputType = inputType;
        chatPersistenceMapper.insertChatMessage(message);
    }

    private void insertAssistantMessage(
            String sessionId,
            String userId,
            String content,
            Long sceneId,
            String areaCode,
            String areaName,
            String currentSpotId,
            String currentSpotName,
            Long areaId,
            String messageId,
            String rewrittenQuestion,
            String intent,
            String inputType,
            String audioUrl,
            String audioFormat,
            String ttsError,
            String currentEntity,
            String sources
    ) {
        ChatMessage message = baseMessage(sessionId, userId, "assistant", content, sceneId, areaCode, areaName,
                currentSpotId, currentSpotName, areaId);
        message.sourceType = "llm";
        message.messageId = nullIfBlank(messageId);
        message.rewrittenQuestion = nullIfBlank(rewrittenQuestion);
        message.intent = nullIfBlank(intent);
        message.intentType = nullIfBlank(intent);
        message.inputType = inputType;
        message.audioUrl = nullIfBlank(audioUrl);
        message.audioFormat = nullIfBlank(audioFormat);
        message.ttsError = nullIfBlank(ttsError);
        message.currentEntity = currentEntity;
        message.sources = sources;
        chatPersistenceMapper.insertChatMessage(message);
    }

    private ChatMessage baseMessage(
            String sessionId,
            String userId,
            String role,
            String content,
            Long sceneId,
            String areaCode,
            String areaName,
            String currentSpotId,
            String currentSpotName,
            Long areaId
    ) {
        ChatMessage message = new ChatMessage();
        message.sessionId = sessionId;
        message.userId = userId;
        message.role = role;
        message.content = content;
        message.sceneId = sceneId;
        message.areaCode = areaCode;
        message.areaName = areaName;
        message.currentSpotId = currentSpotId;
        message.currentSpotName = currentSpotName;
        message.areaId = areaId;
        return message;
    }

    private Long resolveAreaId(Long areaId, String areaCode) {
        if (areaId != null) {
            return areaId;
        }

        String safeAreaCode = firstNotBlank(areaCode);
        if (safeAreaCode.isEmpty()) {
            return null;
        }

        return chatPersistenceMapper.selectAreaIdByAreaCode(safeAreaCode);
    }

    private Long resolveSceneId(String currentSpotId) {
        String value = firstNotBlank(currentSpotId);
        if (value.isEmpty()) {
            return null;
        }

        try {
            Long spotId = Long.parseLong(value);
            return chatPersistenceMapper.selectSpotIdById(spotId);
        } catch (NumberFormatException ignored) {
            return chatPersistenceMapper.selectSpotIdBySceneCode(value);
        }
    }

    private void bindVisitIdSafely(String sessionId, String userId, Long visitId) {
        if (visitId == null || !hasText(sessionId) || !hasText(userId)) {
            return;
        }

        try {
            if (hasColumn("chat_session", "visit_id")) {
                chatPersistenceMapper.bindChatSessionVisitId(sessionId, userId, visitId);
            }
            if (hasColumn("chat_message", "visit_id")) {
                chatPersistenceMapper.bindChatMessageVisitId(sessionId, userId, visitId);
            }
        } catch (Exception e) {
            log.warn("绑定聊天记录 visitId 失败，不影响AI响应。visitId={}, sessionId={}, userId={}, error={}",
                    visitId, sessionId, userId, e.getMessage());
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        try {
            Integer count = chatPersistenceMapper.countTableColumn(tableName, columnName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("检查表字段失败，跳过聊天 visitId 兼容写入。table={}, column={}, error={}",
                    tableName, columnName, e.getMessage());
            return false;
        }
    }

    private Long parseLongOrNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSessionName(String question) {
        String value = firstNotBlank(question, "AI导览会话");
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private String generateSessionId() {
        return "chat_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String nullIfBlank(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }

        return "";
    }
}
