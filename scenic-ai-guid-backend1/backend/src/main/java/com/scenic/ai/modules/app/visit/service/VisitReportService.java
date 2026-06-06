package com.scenic.ai.modules.app.visit.service;

import com.scenic.ai.modules.app.visit.dto.BehaviorStatsDto;
import com.scenic.ai.modules.app.visit.dto.RecommendParkDto;
import com.scenic.ai.modules.app.visit.dto.SatisfactionInfoDto;
import com.scenic.ai.modules.app.visit.dto.VisitReportResponse;
import com.scenic.ai.modules.app.visit.dto.VisitReportSpotDto;
import com.scenic.ai.modules.app.visit.dto.VisitReportDetailResponse;
import com.scenic.ai.modules.app.visit.mapper.VisitReportMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
public class VisitReportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VisitReportMapper visitReportMapper;

    public VisitReportService(VisitReportMapper visitReportMapper) {
        this.visitReportMapper = visitReportMapper;
    }

    public VisitReportResponse getVisitReport(Long visitId) {
        if (visitId == null) {
            throw new IllegalArgumentException("visitId 不能为空");
        }

        VisitReportResponse report = visitReportMapper.selectVisitReportBase(visitId);
        if (report == null) {
            throw new IllegalArgumentException("游玩记录不存在");
        }

        report.startTime = formatDateTime(report.rawStartTime);
        report.endTime = formatDateTime(report.rawEndTime);
        report.stayDuration = resolveStayDuration(report.stayDuration, report.rawStartTime, report.rawEndTime);
        report.stayDurationText = formatDuration(report.stayDuration);
        report.durationSeconds = report.stayDuration;
        report.durationText = report.stayDurationText;
        report.areaName = report.parkName;
        fillTravelSnapshot(report, visitId);
        fillCostDefaults(report);

        List<VisitReportSpotDto> spots = safeList(visitReportMapper.selectVisitReportSpots(visitId));
        for (VisitReportSpotDto spot : spots) {
            spot.enterTime = formatDateTime(spot.rawEnterTime);
            spot.leaveTime = formatDateTime(spot.rawLeaveTime);
            spot.staySeconds = resolveStayDuration(spot.staySeconds, spot.rawEnterTime, spot.rawLeaveTime);
            spot.stayDurationText = formatDuration(spot.staySeconds);
            spot.durationSeconds = spot.staySeconds;
            spot.durationText = spot.stayDurationText;
            spot.scenicName = firstNotBlank(spot.scenicName, "未知景点");
            spot.spotName = spot.scenicName;
        }
        report.spots = spots;
        report.visitedSpots = spots;
        report.spotCount = spots.size();
        report.visitedSpotCount = report.spotCount;

        BehaviorStatsDto behaviorStats = hasColumn("tourist_behavior_event", "visit_id")
                ? visitReportMapper.selectBehaviorStats(visitId)
                : null;
        int behaviorQuestionCount = behaviorStats == null || behaviorStats.aiQuestionCount == null
                ? 0
                : behaviorStats.aiQuestionCount;
        int chatQuestionCount = selectQuestionCountSafely(visitId);
        report.aiQuestionCount = Math.max(behaviorQuestionCount, chatQuestionCount);
        report.favoriteCount = behaviorStats == null || behaviorStats.favoriteCount == null
                ? 0
                : behaviorStats.favoriteCount;
        report.chatCount = report.aiQuestionCount;

        SatisfactionInfoDto satisfactionInfo = hasColumn("tourist_satisfaction_record", "visit_id")
                ? visitReportMapper.selectSatisfactionInfo(visitId)
                : null;
        if (satisfactionInfo != null) {
            report.satisfaction = satisfactionInfo.satisfaction;
            report.comment = satisfactionInfo.comment;
        } else {
            report.satisfaction = report.sessionSatisfaction == null
                    ? null
                    : BigDecimal.valueOf(report.sessionSatisfaction);
            report.comment = report.sessionComment;
        }

        report.recommendParks = report.areaId == null
                ? Collections.emptyList()
                : safeList(visitReportMapper.selectRecommendParks(report.areaId));
        fillReportSummaries(report);

        return report;
    }

    public VisitReportDetailResponse getVisitReportDetail(Long visitId, String userId) {
        VisitReportResponse report = getVisitReport(visitId);
        if (userId != null && !userId.trim().isEmpty()
                && report.userId != null
                && !userId.trim().equals(report.userId)) {
            throw new IllegalArgumentException("当前用户与游览会话不匹配");
        }

        Integer questionCount = defaultInt(report.aiQuestionCount);

        Integer favoriteCount = Math.max(
                defaultInt(report.favoriteCount),
                defaultInt(visitReportMapper.selectFavoriteCountForVisit(visitId))
        );

        VisitReportDetailResponse detail = new VisitReportDetailResponse();
        detail.visitId = report.visitId == null ? null : String.valueOf(report.visitId);
        detail.areaId = report.areaId == null ? null : String.valueOf(report.areaId);
        detail.parkId = report.parkId;
        detail.areaName = report.parkName;
        detail.parkName = report.parkName;
        detail.startTime = report.startTime;
        detail.endTime = report.endTime;
        detail.duration = report.stayDuration;
        detail.durationSeconds = report.durationSeconds;
        detail.durationText = report.durationText;
        detail.durationMinutes = toMinutes(report.stayDuration);
        detail.spotCount = report.spotCount == null ? 0 : report.spotCount;
        detail.visitedSpotCount = detail.spotCount;
        detail.groupSize = firstNotBlank(report.groupSize);
        detail.travelPeopleCount = firstNotBlank(report.travelPeopleCount, report.groupSize);
        detail.travelType = firstNotBlank(report.travelType);
        detail.visitPreference = firstNotBlank(report.visitPreference);
        detail.travelPreference = firstNotBlank(report.travelPreference, report.visitPreference);
        detail.estimatedDuration = firstNotBlank(report.estimatedDuration);
        detail.questionCount = questionCount;
        detail.aiQuestionCount = questionCount;
        detail.chatCount = questionCount;
        detail.favoriteCount = favoriteCount;
        detail.travelInfo.peopleCount = firstNotBlank(detail.travelPeopleCount, detail.groupSize);
        detail.travelInfo.travelType = firstNotBlank(detail.travelType);
        detail.travelInfo.preference = firstNotBlank(detail.travelPreference, detail.visitPreference);

        for (VisitReportSpotDto spot : safeList(report.spots)) {
            VisitReportDetailResponse.SpotStayItem item = new VisitReportDetailResponse.SpotStayItem();
            item.spotId = firstNotBlank(spot.spotId, spot.scenicId);
            item.spotName = firstNotBlank(spot.scenicName, "未知景点");
            item.enterTime = spot.enterTime;
            item.leaveTime = spot.leaveTime;
            item.startTime = spot.enterTime;
            item.endTime = spot.leaveTime;
            item.durationSeconds = spot.staySeconds;
            item.durationText = spot.stayDurationText;
            detail.spotStayList.add(item);
        }

        detail.visitedSpots = detail.spotStayList;
        detail.behaviorSummary.putAll(report.behaviorSummary);
        detail.consumptionSummary.putAll(report.consumptionSummary);
        detail.recommendationSimilarScenic = report.recommendParks;
        detail.summary = buildSummary(detail);
        return detail;
    }

    private void fillReportSummaries(VisitReportResponse report) {
        int spotCount = report.spotCount == null ? 0 : report.spotCount;
        int totalSpotDurationSeconds = 0;
        for (VisitReportSpotDto spot : safeList(report.spots)) {
            totalSpotDurationSeconds += defaultInt(spot.staySeconds);
        }

        report.behaviorSummary.put("spotCount", spotCount);
        report.behaviorSummary.put("visitedSpotCount", spotCount);
        report.behaviorSummary.put("totalSpotDurationSeconds", totalSpotDurationSeconds);
        report.behaviorSummary.put("totalSpotDurationText", formatDuration(totalSpotDurationSeconds));
        report.behaviorSummary.put("aiQuestionCount", defaultInt(report.aiQuestionCount));
        report.behaviorSummary.put("chatCount", defaultInt(report.chatCount));
        report.behaviorSummary.put("favoriteCount", defaultInt(report.favoriteCount));

        report.consumptionSummary.put("ticketCost", report.ticketCost);
        report.consumptionSummary.put("foodCost", report.foodCost);
        report.consumptionSummary.put("shoppingCost", report.shoppingCost);
        report.consumptionSummary.put("transportCost", report.transportCost);
        report.consumptionSummary.put("entertainmentCost", report.entertainmentCost);
        report.consumptionSummary.put("totalCost", report.totalCost);
        report.consumptionSummary.put("consumeStatus", firstNotBlank(report.consumeStatus, "pending"));
        report.consumptionSummary.put("confirmText", "消费记录待景区管理员确认");
    }

    private void fillTravelSnapshot(VisitReportResponse report, Long visitId) {
        String startGroupSize = readVisitStartExtraValue(visitId, "groupSize");
        String startPeopleCount = readVisitStartExtraValue(visitId, "travelPeopleCount");
        String startTravelType = readVisitStartExtraValue(visitId, "travelType");
        String startVisitPreference = readVisitStartExtraValue(visitId, "visitPreference");
        String startPreference = readVisitStartExtraValue(visitId, "travelPreference");
        String startEstimatedDuration = readVisitStartExtraValue(visitId, "estimatedDuration");

        report.travelPeopleCount = firstNotBlank(startPeopleCount, report.travelPeopleCount, startGroupSize, report.groupSize);
        report.groupSize = firstNotBlank(startGroupSize, report.groupSize, report.travelPeopleCount);
        report.travelType = firstNotBlank(startTravelType, report.travelType);
        report.travelPreference = firstNotBlank(startPreference, report.travelPreference, startVisitPreference, report.visitPreference);
        report.visitPreference = firstNotBlank(startVisitPreference, report.visitPreference, report.travelPreference);
        report.estimatedDuration = firstNotBlank(startEstimatedDuration);
    }

    private String readVisitStartExtraValue(Long visitId, String jsonKey) {
        if (visitId == null || jsonKey == null || jsonKey.trim().isEmpty()) {
            return "";
        }
        if (!hasColumn("tourist_behavior_event", "extra_json")) {
            return "";
        }
        try {
            String value = visitReportMapper.selectVisitStartExtraValue(visitId, jsonKey.trim());
            return "null".equalsIgnoreCase(firstNotBlank(value)) ? "" : firstNotBlank(value);
        } catch (Exception e) {
            return "";
        }
    }

    private int selectQuestionCountSafely(Long visitId) {
        int questionCount = 0;
        try {
            questionCount = Math.max(questionCount, defaultInt(visitReportMapper.selectQuestionCountForVisit(visitId)));
        } catch (Exception ignored) {
        }

        if (hasColumn("chat_message", "visit_id")) {
            try {
                questionCount = Math.max(questionCount, defaultInt(visitReportMapper.selectQuestionCountByMessageVisitId(visitId)));
            } catch (Exception ignored) {
            }
        }

        if (hasColumn("chat_session", "visit_id")) {
            try {
                questionCount = Math.max(questionCount, defaultInt(visitReportMapper.selectQuestionCountByChatSessionVisitId(visitId)));
            } catch (Exception ignored) {
            }
        }
        return questionCount;
    }

    private void fillCostDefaults(VisitReportResponse report) {
        report.ticketCost = defaultMoney(report.ticketCost);
        report.foodCost = defaultMoney(report.foodCost);
        report.shoppingCost = defaultMoney(report.shoppingCost);
        report.transportCost = defaultMoney(report.transportCost);
        report.entertainmentCost = defaultMoney(report.entertainmentCost);
        report.totalCost = defaultMoney(report.totalCost);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer count = visitReportMapper.countTableColumn(tableName, columnName);
        return count != null && count > 0;
    }

    private Integer resolveStayDuration(
            Integer savedDuration,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (savedDuration != null) {
            return savedDuration;
        }

        if (startTime == null || endTime == null || endTime.isBefore(startTime)) {
            return null;
        }

        long seconds = Duration.between(startTime, endTime).getSeconds();
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    private String formatDuration(Integer seconds) {
        if (seconds == null) {
            return "";
        }

        if (seconds <= 0) {
            return "0分钟";
        }

        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;

        if (hours > 0 && minutes > 0) {
            return hours + "小时" + minutes + "分钟";
        }

        if (hours > 0) {
            return hours + "小时";
        }

        if (minutes > 0) {
            return minutes + "分钟";
        }

        return seconds + "秒";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER);
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private Integer toMinutes(Integer seconds) {
        if (seconds == null || seconds <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(seconds / 60.0));
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String buildSummary(VisitReportDetailResponse detail) {
        String areaName = firstNotBlank(detail.areaName, "当前景区");
        int spotCount = detail.spotCount == null ? 0 : detail.spotCount;
        int questionCount = detail.questionCount == null ? 0 : detail.questionCount;
        String summary = "本次你完成了" + areaName + "的现场导览，浏览了 "
                + spotCount + " 个景点，向 AI 数字人提问 "
                + questionCount + " 次。";
        if (detail.consumeList == null || detail.consumeList.isEmpty()) {
            summary += " 消费记录待景区管理员确认。";
        }
        return summary;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }
}
