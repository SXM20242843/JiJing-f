package com.scenic.ai.modules.app.user.service;

import com.scenic.ai.modules.app.user.dto.ConsultHistoryItemDto;
import com.scenic.ai.modules.app.user.mapper.ChatHistoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsultHistoryService {

    private final AppUserService appUserService;
    private final ChatHistoryMapper chatHistoryMapper;

    public ConsultHistoryService(
            AppUserService appUserService,
            ChatHistoryMapper chatHistoryMapper
    ) {
        this.appUserService = appUserService;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    public List<ConsultHistoryItemDto> listHistory(
            String authorization,
            String providedUserId,
            Integer limit
    ) {
        String userId = appUserService.resolveRequiredUserId(authorization, providedUserId);

        int finalLimit = limit == null ? 50 : limit;
        if (finalLimit <= 0) {
            finalLimit = 50;
        }
        if (finalLimit > 100) {
            finalLimit = 100;
        }

        List<ConsultHistoryItemDto> list = chatHistoryMapper.selectConsultHistory(userId, finalLimit);

        for (ConsultHistoryItemDto item : list) {
            item.setSourceName(resolveSourceName(item.getSource(), item.getInputType()));
        }

        return list;
    }

    private String resolveSourceName(String source, String inputType) {
        String sourceText = source == null ? "" : source;
        String inputText = inputType == null ? "" : inputType;

        if ("voice".equalsIgnoreCase(inputText) || sourceText.toLowerCase().contains("voice")) {
            return "语音问答";
        }

        if (sourceText.contains("native-live2d") || sourceText.contains("guide")) {
            return "AI 数字人导览";
        }

        return "AI 导览问答";
    }
}