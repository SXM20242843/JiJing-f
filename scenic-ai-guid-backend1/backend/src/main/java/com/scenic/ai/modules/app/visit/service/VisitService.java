package com.scenic.ai.modules.app.visit.service;

import com.scenic.ai.modules.app.visit.dto.SpotEnterRequest;
import com.scenic.ai.modules.app.visit.dto.SpotEnterResponse;
import com.scenic.ai.modules.app.visit.dto.SpotLeaveRequest;
import com.scenic.ai.modules.app.visit.dto.SpotLeaveResponse;
import com.scenic.ai.modules.app.visit.dto.VisitEndRequest;
import com.scenic.ai.modules.app.visit.dto.VisitEndResponse;
import com.scenic.ai.modules.app.visit.dto.VisitStartRequest;
import com.scenic.ai.modules.app.visit.dto.VisitStartResponse;
import com.scenic.ai.modules.app.visit.dto.VisitStatusResponse;
import com.scenic.ai.modules.app.user.dto.BehaviorEventRequest;
import com.scenic.ai.modules.app.user.service.BehaviorEventService;
import com.scenic.ai.modules.app.visit.entity.TouristSpotVisitRecord;
import com.scenic.ai.modules.app.visit.entity.TouristVisitSession;
import com.scenic.ai.modules.app.visit.mapper.TouristSpotVisitRecordMapper;
import com.scenic.ai.modules.app.visit.mapper.TouristVisitSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class VisitService {

    private static final String STATUS_VISITING = "ONGOING";
    private static final String STATUS_FINISHED = "COMPLETED";
    private static final String STATUS_ACTIVE_COMPAT = "ONGOING";
    private static final String STATUS_ENDED_COMPAT = "COMPLETED";
    private static final String CONSUME_STATUS_PENDING = "pending";
    private static final long STALE_ACTIVE_VISIT_HOURS = 12L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter VISIT_NO_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final TouristVisitSessionMapper touristVisitSessionMapper;
    private final TouristSpotVisitRecordMapper touristSpotVisitRecordMapper;
    private final BehaviorEventService behaviorEventService;

    public VisitService(
            TouristVisitSessionMapper touristVisitSessionMapper,
            TouristSpotVisitRecordMapper touristSpotVisitRecordMapper,
            BehaviorEventService behaviorEventService
    ) {
        this.touristVisitSessionMapper = touristVisitSessionMapper;
        this.touristSpotVisitRecordMapper = touristSpotVisitRecordMapper;
        this.behaviorEventService = behaviorEventService;
    }

    @Transactional
    public VisitStartResponse startVisit(VisitStartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        String userId = request.getUserIdText();
        String groupSize = firstNotBlank(request.getTravelPeopleCountText(), request.getGroupSizeText());
        String travelType = request.getTravelTypeText();
        String visitPreference = firstNotBlank(request.getTravelPreferenceText(), request.getVisitPreferenceText());
        String parkId = request.getParkIdText();
        String parkName = request.getParkNameText();
        String estimatedDuration = request.getEstimatedDurationText();

        if (userId.isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        validateCoreUserId(userId);

        Long areaId = resolveAreaId(request);
        if (areaId == null) {
            throw new IllegalArgumentException("未找到对应景区，无法创建现场导览记录");
        }
        String areaName = firstNotBlank(parkName, touristVisitSessionMapper.selectAreaNameById(areaId));
        String areaCode = firstNotBlank(request.getAreaCodeText(), touristVisitSessionMapper.selectAreaCodeById(areaId), parkId);

        LocalDateTime now = LocalDateTime.now();
        TouristVisitSession activeSession = touristVisitSessionMapper.selectActiveSessionByUserAndAreaId(userId, areaId);
        if (activeSession != null && activeSession.id != null) {
            if (isStaleActiveVisit(activeSession, now)) {
                long staleHours = activeSession.startTime == null
                        ? STALE_ACTIVE_VISIT_HOURS
                        : Duration.between(activeSession.startTime, now).toHours();
                log.warn("[VisitStart] stale active visit auto closed visitId={}, startTime={}, hours={}",
                        activeSession.id, activeSession.startTime, staleHours);
                closeStaleActiveVisit(activeSession, request, now, areaName);
                log.info("[VisitStart] create new visit after stale closed");
            } else {
                log.info("[VisitStart] reuse active visitId={}", activeSession.id);
                updateActiveVisitStartSnapshot(activeSession, request, groupSize, travelType, visitPreference, now);
                recordVisitStartEvent(request, activeSession, areaCode, areaName, estimatedDuration);
                return buildVisitStartResponse(activeSession, areaCode, areaName, STATUS_ACTIVE_COMPAT);
            }
        }

        TouristVisitSession session = new TouristVisitSession();
        session.visitNo = generateVisitNo(now);
        session.userId = userId;
        session.chatSessionId = null;
        session.areaId = areaId;
        session.startTime = now;
        session.startLongitude = request.longitude;
        session.startLatitude = request.latitude;
        session.visitStatus = STATUS_VISITING;
        session.sourceType = safeSourceType(request.getStartSourceText());
        session.groupSize = emptyToNull(groupSize);
        session.travelType = emptyToNull(travelType);
        session.visitPreference = emptyToNull(visitPreference);
        session.consumeStatus = CONSUME_STATUS_PENDING;
        session.ticketCost = BigDecimal.ZERO;
        session.foodCost = BigDecimal.ZERO;
        session.shoppingCost = BigDecimal.ZERO;
        session.transportCost = BigDecimal.ZERO;
        session.entertainmentCost = BigDecimal.ZERO;
        session.totalCost = BigDecimal.ZERO;
        session.createdAt = now;
        session.updatedAt = now;

        int rows = touristVisitSessionMapper.insertVisitSession(session);
        if (rows <= 0 || session.id == null) {
            throw new IllegalStateException("创建游玩记录失败");
        }

        recordVisitStartEvent(request, session, areaCode, areaName, estimatedDuration);

        return buildVisitStartResponse(session, areaCode, areaName, STATUS_ACTIVE_COMPAT);
    }

    @Transactional
    public SpotEnterResponse enterScenic(SpotEnterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        Long visitId = request.getVisitIdValue();
        String scenicId = firstNotBlank(request.getScenicIdText(), request.getSpotNameText());

        if (visitId == null) {
            throw new IllegalArgumentException("visitId 不能为空");
        }

        if (scenicId.isEmpty()) {
            throw new IllegalArgumentException("scenicId 不能为空");
        }

        TouristVisitSession session = touristSpotVisitRecordMapper.selectVisitSessionById(visitId);
        if (session == null || session.areaId == null) {
            throw new IllegalArgumentException("未找到对应游玩会话");
        }
        validateCoreUserId(session.userId);
        if (!isRunningStatus(session.visitStatus, session.endTime)) {
            throw new IllegalArgumentException("游玩会话已结束，不能进入景点");
        }

        validateVisitOwner(request.getUserIdText(), session.userId);

        Long spotId = touristSpotVisitRecordMapper.selectSpotIdByScenicId(session.areaId, scenicId);
        LocalDateTime now = LocalDateTime.now();
        TouristSpotVisitRecord activeRecord = touristSpotVisitRecordMapper.selectLatestOpenSpotVisitRecord(visitId);
        if (activeRecord != null && isSameSpot(activeRecord, spotId, scenicId)) {
            recordLocationLogSafely(session, activeRecord.spotId, request.longitude, request.latitude, request.getEnterSourceText(), now);
            recordSpotEnterEvent(request, session, activeRecord, scenicId);
            return buildSpotEnterResponse(activeRecord, scenicId, request.getSpotNameText());
        }
        if (activeRecord != null) {
            autoCloseOpenSpot(session, activeRecord, request.longitude, request.latitude, "auto_spot_enter");
        }

        TouristSpotVisitRecord record = new TouristSpotVisitRecord();
        record.userId = firstNotBlank(request.getUserIdText(), session.userId);
        record.visitId = visitId;
        record.areaId = session.areaId;
        record.spotId = spotId;
        record.frontendScenicId = scenicId;
        record.frontendScenicName = request.getScenicNameText();
        record.enterTime = now;
        record.enterLongitude = request.longitude;
        record.enterLatitude = request.latitude;
        record.sourceType = safeSourceType(request.getEnterSourceText());
        record.createdAt = now;
        record.updatedAt = now;

        int rows = touristSpotVisitRecordMapper.insertSpotVisitRecord(record);
        if (rows <= 0 || record.id == null) {
            throw new IllegalStateException("创建景点停留记录失败");
        }

        recordLocationLogSafely(session, record.spotId, request.longitude, request.latitude, record.sourceType, now);
        recordSpotEnterEvent(request, session, record, scenicId);

        return buildSpotEnterResponse(record, scenicId, request.getSpotNameText());
    }

    @Transactional
    public SpotLeaveResponse leaveScenic(SpotLeaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        Long visitId = request.getVisitIdValue();
        String scenicId = firstNotBlank(request.getScenicIdText(), request.getSpotNameText());

        if (visitId == null) {
            throw new IllegalArgumentException("visitId 不能为空");
        }

        TouristVisitSession session = touristSpotVisitRecordMapper.selectVisitSessionById(visitId);
        if (session == null || session.areaId == null) {
            throw new IllegalArgumentException("未找到对应游玩会话");
        }
        validateCoreUserId(session.userId);

        validateVisitOwner(request.getUserIdText(), session.userId);

        Long spotId = scenicId.isEmpty() ? null : touristSpotVisitRecordMapper.selectSpotIdByScenicId(session.areaId, scenicId);
        TouristSpotVisitRecord record = scenicId.isEmpty()
                ? touristSpotVisitRecordMapper.selectLatestOpenSpotVisitRecord(visitId)
                : touristSpotVisitRecordMapper.selectOpenSpotVisitRecord(visitId, spotId, scenicId);
        if (record == null) {
            record = touristSpotVisitRecordMapper.selectLatestOpenSpotVisitRecord(visitId);
        }

        if (record == null) {
            recordSpotLeaveNoopEvent(request, session, scenicId);
            return buildSpotLeaveResponse(visitId, scenicId, spotId, request.getSpotNameText(), 0);
        }

        LocalDateTime leaveTime = LocalDateTime.now();
        int staySeconds = calculateStaySeconds(record.enterTime, leaveTime);

        int rows = touristSpotVisitRecordMapper.updateSpotVisitLeave(
                record.id,
                leaveTime,
                request.longitude,
                request.latitude,
                staySeconds
        );
        if (rows <= 0) {
            throw new IllegalStateException("更新景点停留记录失败");
        }

        record.leaveTime = leaveTime;
        record.leaveLongitude = request.longitude;
        record.leaveLatitude = request.latitude;
        record.staySeconds = staySeconds;
        recordSpotLeaveEvent(request, session, record, scenicId, staySeconds);

        return buildSpotLeaveResponse(
                visitId,
                firstNotBlank(scenicId, record.frontendScenicId),
                record.spotId,
                firstNotBlank(request.getSpotNameText(), record.frontendScenicName),
                staySeconds
        );
    }

    @Transactional
    public VisitEndResponse endVisit(VisitEndRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        Long visitId = request.getVisitIdValue();
        if (visitId == null) {
            throw new IllegalArgumentException("visitId 不能为空");
        }

        TouristVisitSession session = touristVisitSessionMapper.selectVisitSessionForEnd(visitId);
        if (session == null) {
            throw new IllegalArgumentException("未找到对应游玩会话");
        }
        validateCoreUserId(session.userId);

        validateVisitOwner(request.getUserIdText(), session.userId);

        if (isEndedStatus(session.visitStatus) || session.endTime != null) {
            return buildVisitEndResponse(session);
        }

        TouristSpotVisitRecord activeRecord = touristSpotVisitRecordMapper.selectLatestOpenSpotVisitRecord(visitId);
        if (activeRecord != null) {
            autoCloseOpenSpot(session, activeRecord, request.longitude, request.latitude, "visit_end");
        }

        LocalDateTime endTime = LocalDateTime.now();
        Integer totalDurationSeconds = calculateStaySeconds(session.startTime, endTime);

        int rows = touristVisitSessionMapper.updateVisitEnd(
                visitId,
                endTime,
                request.longitude,
                request.latitude,
                totalDurationSeconds
        );

        if (rows <= 0) {
            throw new IllegalStateException("结束现场导览失败");
        }
        writeEndReasonSafely(visitId, firstNotBlank(request.end_reason, request.endReason, request.getEndSourceText()));

        session.endTime = endTime;
        session.totalDurationSeconds = totalDurationSeconds;
        session.visitStatus = STATUS_FINISHED;

        recordVisitEndEvent(request, session);

        return buildVisitEndResponse(session);
    }

    public VisitStatusResponse getVisitStatus(Long visitId, String userId) {
        if (visitId == null) {
            throw new IllegalArgumentException("visitId 不能为空");
        }

        VisitStatusResponse response = touristVisitSessionMapper.selectVisitStatusById(visitId);
        if (response == null) {
            throw new IllegalArgumentException("未找到对应游玩会话");
        }

        VisitStatusResponse normalized = normalizeVisitStatusResponse(response);
        String requestUserId = firstNotBlank(userId);
        String sessionUserId = firstNotBlank(normalized.userId);

        if (!requestUserId.isEmpty() && !sessionUserId.isEmpty() && !requestUserId.equals(sessionUserId)) {
            // 结束导览后，uni-app 会立刻按 visitId 查询状态来清理首页缓存和打开报告。
            // 历史进行中 visit 可能存在数字主键 / tourist_user.user_id 混用；如果这条 visit 已完成，
            // 允许返回 COMPLETED 状态，避免首页继续显示“现场导览进行中”。
            if (isEndedStatus(normalized.status) || normalized.rawEndTime != null) {
                log.warn("查询已结束导览状态时 userId 口径不一致，已允许返回完成状态。visitId={}, requestUserId={}, sessionUserId={}",
                        visitId, requestUserId, sessionUserId);
                return normalized;
            }
        }

        validateVisitOwner(userId, normalized.userId);
        return normalized;
    }

    public VisitStatusResponse getCurrentVisitStatus(String userId, String areaIdText) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        Long areaId = resolveAreaIdText(areaIdText);
        VisitStatusResponse response = touristVisitSessionMapper.selectLatestVisitStatus(userId, areaId);
        return response == null ? null : normalizeVisitStatusResponse(response);
    }

    public VisitStatusResponse getVisitOverview(String userId, String areaIdText) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        Long areaId = resolveAreaIdText(areaIdText);
        VisitStatusResponse running = touristVisitSessionMapper.selectRunningVisitStatus(userId, areaId);
        VisitStatusResponse lastReport = touristVisitSessionMapper.selectLastCompletedVisitStatus(userId, areaId);

        VisitStatusResponse response = running == null ? new VisitStatusResponse() : normalizeVisitStatusResponse(running);
        response.userId = userId;
        response.hasRunningVisit = running != null;
        if (running != null) {
            response.status = STATUS_ACTIVE_COMPAT;
            response.startedAt = response.startTime;
        }

        response.hasLastReport = lastReport != null;
        if (lastReport != null) {
            VisitStatusResponse normalizedReport = normalizeVisitStatusResponse(lastReport);
            response.lastReportVisitId = normalizedReport.visitId;
            response.lastReportAreaName = normalizedReport.areaName;
            response.lastFinishedAt = normalizedReport.endTime;
        }

        if (running == null) {
            response.visitId = null;
            response.areaId = areaId == null ? null : String.valueOf(areaId);
            response.areaName = null;
            response.status = null;
            response.startTime = null;
            response.startedAt = null;
            response.endTime = null;
            response.durationSeconds = null;
            response.reportReady = false;
        }
        return response;
    }

    public void bindChatSessionId(Long visitId, String userId, String chatSessionId) {
        if (visitId == null || userId == null || userId.trim().isEmpty()
                || chatSessionId == null || chatSessionId.trim().isEmpty()) {
            return;
        }
        touristVisitSessionMapper.bindChatSessionIdIfAbsent(visitId, userId.trim(), chatSessionId.trim());
    }

    private void writeEndReasonSafely(Long visitId, String endReason) {
        if (visitId == null || endReason == null || endReason.trim().isEmpty()) {
            return;
        }
        try {
            Integer count = touristVisitSessionMapper.countTableColumn("tourist_visit_session", "end_reason");
            if (count != null && count > 0) {
                touristVisitSessionMapper.updateVisitEndReason(visitId, endReason.trim());
            }
        } catch (Exception e) {
            log.warn("写入游览结束原因失败，不影响结束导览流程。visitId={}, error={}",
                    visitId, e.getMessage());
        }
    }

    private boolean isStaleActiveVisit(TouristVisitSession session, LocalDateTime now) {
        if (session == null || session.startTime == null || now == null) {
            return false;
        }
        return Duration.between(session.startTime, now).toHours() >= STALE_ACTIVE_VISIT_HOURS;
    }

    private void closeStaleActiveVisit(
            TouristVisitSession session,
            VisitStartRequest request,
            LocalDateTime endTime,
            String areaName
    ) {
        TouristSpotVisitRecord activeRecord = touristSpotVisitRecordMapper.selectLatestOpenSpotVisitRecord(session.id);
        if (activeRecord != null) {
            autoCloseOpenSpot(session, activeRecord, request.longitude, request.latitude, "stale_auto_closed");
        }

        int realDurationSeconds = calculateStaySeconds(session.startTime, endTime);
        int cappedDurationSeconds = Math.min(realDurationSeconds, (int) (STALE_ACTIVE_VISIT_HOURS * 3600));
        int rows = touristVisitSessionMapper.updateVisitEnd(
                session.id,
                endTime,
                request.longitude,
                request.latitude,
                cappedDurationSeconds
        );
        if (rows <= 0) {
            throw new IllegalStateException("关闭历史现场导览失败");
        }
        writeEndReasonSafely(session.id, "stale_auto_closed");

        session.endTime = endTime;
        session.endLongitude = request.longitude;
        session.endLatitude = request.latitude;
        session.totalDurationSeconds = cappedDurationSeconds;
        session.visitStatus = STATUS_FINISHED;

        VisitEndRequest endRequest = new VisitEndRequest();
        endRequest.visitId = session.id;
        endRequest.userId = session.userId;
        endRequest.areaId = session.areaId;
        endRequest.parkName = areaName;
        endRequest.longitude = request.longitude;
        endRequest.latitude = request.latitude;
        endRequest.source = "stale_auto_closed";
        endRequest.endReason = "stale_auto_closed";
        endRequest.trigger = "stale_auto_closed";
        recordVisitEndEvent(endRequest, session);
    }

    private void updateActiveVisitStartSnapshot(
            TouristVisitSession session,
            VisitStartRequest request,
            String groupSize,
            String travelType,
            String visitPreference,
            LocalDateTime updatedAt
    ) {
        if (session == null || session.id == null || request == null) {
            return;
        }

        String safeGroupSize = emptyToNull(groupSize);
        String safeTravelType = emptyToNull(travelType);
        String safeVisitPreference = emptyToNull(visitPreference);
        touristVisitSessionMapper.updateVisitStartSnapshot(
                session.id,
                request.longitude,
                request.latitude,
                emptyToNull(safeSourceType(request.getStartSourceText())),
                safeGroupSize,
                safeTravelType,
                safeVisitPreference,
                updatedAt == null ? LocalDateTime.now() : updatedAt
        );

        session.startLongitude = request.longitude == null ? session.startLongitude : request.longitude;
        session.startLatitude = request.latitude == null ? session.startLatitude : request.latitude;
        session.sourceType = firstNotBlank(request.getStartSourceText(), session.sourceType);
        session.groupSize = firstNotBlank(safeGroupSize, session.groupSize);
        session.travelType = firstNotBlank(safeTravelType, session.travelType);
        session.visitPreference = firstNotBlank(safeVisitPreference, session.visitPreference);
    }

    private Long resolveAreaId(VisitStartRequest request) {
        if (request == null) {
            return null;
        }

        Long areaId = request.getAreaIdValue();
        if (areaId != null) {
            String areaName = touristVisitSessionMapper.selectAreaNameById(areaId);
            if (!firstNotBlank(areaName).isEmpty()) {
                return areaId;
            }
        }

        return resolveAreaId(request.getParkIdText(), request.getParkNameText());
    }

    private Long resolveAreaId(String parkId, String parkName) {
        Long areaId = null;

        if (!parkId.isEmpty()) {
            areaId = parseLongOrNull(parkId);
            if (areaId != null && firstNotBlank(touristVisitSessionMapper.selectAreaNameById(areaId)).isEmpty()) {
                areaId = null;
            }
            if (areaId == null) {
                areaId = touristVisitSessionMapper.selectAreaIdByAreaCode(parkId);
            }
        }

        if (areaId == null && !parkName.isEmpty()) {
            areaId = touristVisitSessionMapper.selectAreaIdByAreaName(parkName);
        }

        return areaId;
    }

    private Long resolveAreaIdText(String areaIdText) {
        if (areaIdText == null || areaIdText.trim().isEmpty()) {
            return null;
        }

        String value = areaIdText.trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return touristVisitSessionMapper.selectAreaIdByAreaCode(value);
        }
    }

    private String normalizeSourceType(String sourceType) {
        String value = sourceType == null ? "" : sourceType.trim().toUpperCase();
        if ("GPS".equals(value) || "MANUAL".equals(value)) {
            return value;
        }
        return "MANUAL";
    }

    private String generateVisitNo(LocalDateTime now) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "VISIT_" + now.format(VISIT_NO_TIME_FORMATTER) + "_" + random;
    }

    private String safeSourceType(String sourceType) {
        if (sourceType == null || sourceType.trim().isEmpty()) {
            return "ai_guide_click";
        }

        String value = sourceType.trim();
        return value.length() > 30 ? value.substring(0, 30) : value;
    }

    private void validateVisitOwner(String providedUserId, String sessionUserId) {
        if (providedUserId == null || providedUserId.trim().isEmpty()) {
            return;
        }

        if (sessionUserId == null || !providedUserId.trim().equals(sessionUserId)) {
            throw new IllegalArgumentException("当前用户与游览会话不匹配");
        }
    }

    private void validateCoreUserId(String userId) {
        String value = firstNotBlank(userId).toLowerCase();
        if (value.isEmpty()
                || "anonymous".equals(value)
                || value.startsWith("visitor_")
                || value.startsWith("android-live2d-")) {
            throw new IllegalArgumentException("请先登录");
        }
    }

    private VisitStartResponse buildVisitStartResponse(
            TouristVisitSession session,
            String areaCode,
            String areaName,
            String status
    ) {
        VisitStartResponse response = new VisitStartResponse(
                session.id,
                session.visitNo,
                session.userId,
                firstNotBlank(areaCode, session.areaId == null ? "" : String.valueOf(session.areaId)),
                areaName,
                session.areaId,
                formatDateTime(session.startTime),
                status
        );
        response.areaName = areaName;
        return response;
    }

    private SpotEnterResponse buildSpotEnterResponse(
            TouristSpotVisitRecord record,
            String scenicId,
            String spotName
    ) {
        String enterTime = formatDateTime(record.enterTime);
        SpotEnterResponse response = new SpotEnterResponse(
                record.id,
                record.visitId,
                firstNotBlank(scenicId, record.frontendScenicId),
                enterTime
        );
        response.spotId = record.spotId;
        response.spotName = firstNotBlank(spotName, record.frontendScenicName);
        response.currentSpot = new SpotEnterResponse.CurrentSpot(
                record.spotId,
                response.scenicId,
                response.spotName,
                enterTime
        );
        return response;
    }

    private SpotLeaveResponse buildSpotLeaveResponse(
            Long visitId,
            String scenicId,
            Long spotId,
            String spotName,
            Integer staySeconds
    ) {
        SpotLeaveResponse response = new SpotLeaveResponse(visitId, scenicId, staySeconds);
        response.spotId = spotId;
        response.spotName = spotName;
        response.success = true;
        return response;
    }

    private void autoCloseOpenSpot(
            TouristVisitSession session,
            TouristSpotVisitRecord record,
            BigDecimal longitude,
            BigDecimal latitude,
            String trigger
    ) {
        LocalDateTime leaveTime = LocalDateTime.now();
        int staySeconds = calculateStaySeconds(record.enterTime, leaveTime);
        touristSpotVisitRecordMapper.updateSpotVisitLeave(
                record.id,
                leaveTime,
                longitude,
                latitude,
                staySeconds
        );

        SpotLeaveRequest request = new SpotLeaveRequest();
        request.userId = firstNotBlank(record.userId, session.userId);
        request.visitId = record.visitId;
        request.spotId = record.spotId == null ? null : String.valueOf(record.spotId);
        request.scenicId = record.frontendScenicId;
        request.spotName = record.frontendScenicName;
        request.source = trigger;
        request.trigger = trigger;
        request.longitude = longitude;
        request.latitude = latitude;
        record.leaveTime = leaveTime;
        record.leaveLongitude = longitude;
        record.leaveLatitude = latitude;
        record.staySeconds = staySeconds;
        recordSpotLeaveEvent(request, session, record, firstNotBlank(record.frontendScenicId, request.getSpotIdText()), staySeconds);
    }

    private boolean isSameSpot(TouristSpotVisitRecord record, Long spotId, String scenicId) {
        if (record == null) {
            return false;
        }
        if (spotId != null && record.spotId != null && spotId.equals(record.spotId)) {
            return true;
        }
        String recordScenicId = firstNotBlank(record.frontendScenicId);
        String requestScenicId = firstNotBlank(scenicId);
        return !recordScenicId.isEmpty() && recordScenicId.equals(requestScenicId);
    }

    private void recordLocationLogSafely(
            TouristVisitSession session,
            Long spotId,
            BigDecimal longitude,
            BigDecimal latitude,
            String sourceType,
            LocalDateTime logTime
    ) {
        try {
            touristSpotVisitRecordMapper.insertLocationLog(
                    session.userId,
                    session.id,
                    session.areaId,
                    spotId,
                    longitude,
                    latitude,
                    safeSourceType(sourceType),
                    logTime == null ? LocalDateTime.now() : logTime
            );
        } catch (Exception e) {
            log.warn("写入 tourist_location_log 失败，不影响景点到达流程。visitId={}, areaId={}, error={}",
                    session.id, session.areaId, e.getMessage());
        }
    }

    private void recordVisitStartEvent(
            VisitStartRequest request,
            TouristVisitSession session,
            String areaCode,
            String areaName,
            String estimatedDuration
    ) {
        BehaviorEventRequest event = new BehaviorEventRequest();
        event.userId = session.userId;
        event.visitId = session.id;
        event.areaId = session.areaId;
        event.entityType = "AREA";
        event.entityId = session.areaId == null ? firstNotBlank(areaCode) : String.valueOf(session.areaId);
        event.eventType = "visit_start";
        event.eventName = "开启现场导览";
        event.sourcePage = firstNotBlank(request.getStartSourceText(), "visit_start");
        event.longitude = request.longitude;
        event.latitude = request.latitude;
        event.content = areaName;

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("userId", session.userId);
        extra.put("visitId", session.id);
        extra.put("areaId", session.areaId);
        extra.put("parkId", session.areaId);
        extra.put("areaCode", firstNotBlank(areaCode));
        extra.put("parkCode", firstNotBlank(areaCode));
        extra.put("areaName", firstNotBlank(areaName));
        extra.put("parkName", firstNotBlank(areaName));
        extra.put("entrySource", request.getStartSourceText());
        extra.put("startSource", request.getStartSourceText());
        extra.put("groupSize", request.getGroupSizeText());
        extra.put("travelPeopleCount", request.getTravelPeopleCountText());
        extra.put("travelType", request.getTravelTypeText());
        extra.put("visitPreference", request.getVisitPreferenceText());
        extra.put("travelPreference", request.getTravelPreferenceText());
        extra.put("estimatedDuration", firstNotBlank(estimatedDuration));
        event.extra = extra;
        addBehaviorEventSafely(event, session.userId, "visit_start", session.id);
    }

    private void recordSpotLeaveNoopEvent(
            SpotLeaveRequest request,
            TouristVisitSession session,
            String scenicId
    ) {
        try {
            BehaviorEventRequest event = new BehaviorEventRequest();
            event.userId = session.userId;
            event.sessionId = firstNotBlank(request.getSessionIdText(), session.chatSessionId);
            event.visitId = session.id;
            event.areaId = session.areaId;
            event.spotId = parseLongOrNull(request.getSpotIdText());
            event.entityType = "SPOT";
            event.entityId = firstNotBlank(request.getSpotIdText(), scenicId);
            event.eventType = "spot_leave";
            event.eventName = "景点离开";
            event.sourcePage = firstNotBlank(request.getSourceText(), "native-live2d-guide");
            event.longitude = request.longitude;
            event.latitude = request.latitude;
            event.durationSeconds = 0;
            event.content = firstNotBlank(request.getSpotNameText(), scenicId);

            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("userId", session.userId);
            extra.put("visitId", session.id);
            extra.put("areaId", session.areaId);
            extra.put("spotId", request.getSpotIdText());
            extra.put("spotName", request.getSpotNameText());
            extra.put("source", event.sourcePage);
            extra.put("noop", true);
            event.extra = extra;
            addBehaviorEventSafely(event, session.userId, "spot_leave_noop", session.id);
        } catch (Exception e) {
            log.warn("记录无 active spot 的离开事件失败，不影响离开兜底。visitId={}, error={}",
                    session.id, e.getMessage());
        }
    }

    private void recordSpotEnterEvent(
            SpotEnterRequest request,
            TouristVisitSession session,
            TouristSpotVisitRecord record,
            String scenicId
    ) {
        String sessionId = firstNotBlank(request.getSessionIdText(), session.chatSessionId);
        String spotName = firstNotBlank(request.getSpotNameText(), request.getScenicNameText(), scenicId);

        BehaviorEventRequest event = new BehaviorEventRequest();
        event.userId = record.userId;
        event.sessionId = sessionId;
        event.visitId = record.visitId;
        event.areaId = record.areaId;
        event.spotId = record.spotId;
        event.entityType = record.spotId == null ? "OTHER" : "SPOT";
        event.entityId = record.spotId == null ? scenicId : String.valueOf(record.spotId);
        event.eventType = "spot_enter";
        event.eventName = "景点到达";
        event.sourcePage = firstNotBlank(request.getSourceText(), "native-live2d-guide");
        event.longitude = request.longitude;
        event.latitude = request.latitude;
        event.content = spotName;
        event.extra = buildVisitEventExtra(
                record.userId,
                sessionId,
                record.visitId,
                record.areaId,
                request.getParkNameText(),
                record.spotId,
                spotName,
                "",
                "",
                event.sourcePage,
                request.getLocationSourceText(),
                request.getTriggerText(),
                request.getDemoModeValue()
        );
        addBehaviorEventSafely(event, record.userId, "spot_enter", record.visitId);
    }

    private void recordSpotLeaveEvent(
            SpotLeaveRequest request,
            TouristVisitSession session,
            TouristSpotVisitRecord record,
            String scenicId,
            Integer staySeconds
    ) {
        String sessionId = firstNotBlank(request.getSessionIdText(), session.chatSessionId);
        String spotName = firstNotBlank(request.getSpotNameText(), request.getScenicNameText(), record.frontendScenicName, scenicId);

        BehaviorEventRequest event = new BehaviorEventRequest();
        event.userId = record.userId;
        event.sessionId = sessionId;
        event.visitId = record.visitId;
        event.areaId = record.areaId;
        event.spotId = record.spotId;
        event.entityType = record.spotId == null ? "OTHER" : "SPOT";
        event.entityId = record.spotId == null ? scenicId : String.valueOf(record.spotId);
        event.eventType = "spot_leave";
        event.eventName = "景点离开";
        event.sourcePage = firstNotBlank(request.getSourceText(), "native-live2d-guide");
        event.longitude = request.longitude;
        event.latitude = request.latitude;
        event.durationSeconds = staySeconds;
        event.content = spotName;
        event.extra = buildVisitEventExtra(
                record.userId,
                sessionId,
                record.visitId,
                record.areaId,
                request.getParkNameText(),
                record.spotId,
                spotName,
                "",
                "",
                event.sourcePage,
                request.getLocationSourceText(),
                request.getTriggerText(),
                request.getDemoModeValue()
        );
        addBehaviorEventSafely(event, record.userId, "spot_leave", record.visitId);
    }

    private void recordVisitEndEvent(VisitEndRequest request, TouristVisitSession session) {
        String sessionId = firstNotBlank(request.getSessionIdText(), session.chatSessionId);

        BehaviorEventRequest event = new BehaviorEventRequest();
        event.userId = session.userId;
        event.sessionId = sessionId;
        event.visitId = session.id;
        event.areaId = session.areaId;
        event.spotId = parseLongOrNull(request.getSpotIdText());
        event.entityType = "AREA";
        event.entityId = session.areaId == null ? "" : String.valueOf(session.areaId);
        event.eventType = "visit_end";
        event.eventName = "结束游览";
        event.sourcePage = firstNotBlank(request.getSourceText(), "native-live2d-guide");
        event.longitude = request.longitude;
        event.latitude = request.latitude;
        event.durationSeconds = session.totalDurationSeconds;
        event.content = request.getParkNameText();
        event.extra = buildVisitEventExtra(
                session.userId,
                sessionId,
                session.id,
                session.areaId,
                request.getParkNameText(),
                parseLongOrNull(request.getSpotIdText()),
                request.getSpotNameText(),
                "",
                "",
                event.sourcePage,
                request.getLocationSourceText(),
                request.getTriggerText(),
                request.getDemoModeValue()
        );
        addBehaviorEventSafely(event, session.userId, "visit_end", session.id);
    }

    private void addBehaviorEventSafely(
            BehaviorEventRequest event,
            String userId,
            String eventType,
            Long visitId
    ) {
        try {
            behaviorEventService.addBehaviorEvent(event, userId);
        } catch (Exception e) {
            log.warn("记录行为事件失败，不影响现场导览主流程。eventType={}, visitId={}, userId={}, error={}",
                    eventType, visitId, userId, e.getMessage());
        }
    }

    private Map<String, Object> buildVisitEventExtra(
            String userId,
            String sessionId,
            Long visitId,
            Long areaId,
            String parkName,
            Long spotId,
            String spotName,
            String planId,
            String routeName,
            String source,
            String locationSource,
            String trigger,
            Boolean demoMode
    ) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("userId", firstNotBlank(userId));
        extra.put("visitId", visitId);
        extra.put("sessionId", firstNotBlank(sessionId));
        extra.put("areaId", areaId);
        extra.put("parkName", firstNotBlank(parkName));
        extra.put("spotId", spotId);
        extra.put("spotName", firstNotBlank(spotName));
        extra.put("planId", firstNotBlank(planId));
        extra.put("routeName", firstNotBlank(routeName));
        extra.put("source", firstNotBlank(source));
        extra.put("locationSource", firstNotBlank(locationSource));
        extra.put("trigger", firstNotBlank(trigger));
        if (demoMode != null) {
            extra.put("demoMode", demoMode);
        }
        return extra;
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int calculateStaySeconds(LocalDateTime enterTime, LocalDateTime leaveTime) {
        if (enterTime == null || leaveTime == null || leaveTime.isBefore(enterTime)) {
            return 0;
        }

        long seconds = Duration.between(enterTime, leaveTime).getSeconds();
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
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

    private String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private VisitEndResponse buildVisitEndResponse(TouristVisitSession session) {
        return new VisitEndResponse(
                session.id,
                formatDateTime(session.startTime),
                formatDateTime(session.endTime),
                session.totalDurationSeconds,
                normalizeVisitStatus(session.visitStatus, session.endTime)
        );
    }

    private VisitStatusResponse normalizeVisitStatusResponse(VisitStatusResponse response) {
        response.startTime = formatDateTime(response.rawStartTime);
        response.startedAt = response.startTime;
        response.endTime = formatDateTime(response.rawEndTime);
        response.status = normalizeVisitStatus(response.rawStatus, response.rawEndTime);
        response.reportReady = isEndedStatus(response.status);
        response.hasRunningVisit = isRunningStatus(response.rawStatus, response.rawEndTime);
        if (response.durationSeconds == null && response.rawStartTime != null) {
            LocalDateTime end = response.rawEndTime == null ? LocalDateTime.now() : response.rawEndTime;
            response.durationSeconds = calculateStaySeconds(response.rawStartTime, end);
        }
        return response;
    }

    private String normalizeVisitStatus(String status, LocalDateTime endTime) {
        String value = firstNotBlank(status).toUpperCase();
        if (endTime != null
                || STATUS_FINISHED.equals(value)
                || "FINISH".equals(value)
                || "ENDED".equals(value)
                || "COMPLETED".equals(value)) {
            return STATUS_ENDED_COMPAT;
        }

        if (STATUS_VISITING.equals(value)
                || "ACTIVE".equals(value)
                || "IN_PROGRESS".equals(value)
                || "ONGOING".equals(value)) {
            return STATUS_ACTIVE_COMPAT;
        }

        return value.isEmpty() ? STATUS_ACTIVE_COMPAT : value;
    }

    private boolean isEndedStatus(String status) {
        String value = firstNotBlank(status).toUpperCase();
        return STATUS_ENDED_COMPAT.equals(value)
                || STATUS_FINISHED.equals(value)
                || "FINISH".equals(value)
                || "ENDED".equals(value)
                || "COMPLETED".equals(value);
    }

    private boolean isRunningStatus(String status, LocalDateTime endTime) {
        if (endTime != null) {
            return false;
        }

        String value = firstNotBlank(status).toUpperCase();
        return STATUS_ACTIVE_COMPAT.equals(value)
                || STATUS_VISITING.equals(value)
                || "ACTIVE".equals(value)
                || "IN_PROGRESS".equals(value)
                || "ONGOING".equals(value);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * 获取或创建游览会话。
     * 如果当前用户在指定景区有未结束的会话（visit_status = 'VISITING'），则复用该会话；
     * 否则创建新会话。
     */
    public VisitStartResponse getOrCreateVisit(String userId, String parkId, String parkName,
                                               String groupSize, String travelType, String visitPreference,
                                               BigDecimal longitude, BigDecimal latitude) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        validateCoreUserId(userId);
        Long areaId = resolveAreaId(firstNotBlank(parkId), firstNotBlank(parkName));
        if (areaId == null) {
            throw new IllegalArgumentException("parkId 不能为空");
        }
        String areaCode = firstNotBlank(parkId, touristVisitSessionMapper.selectAreaCodeById(areaId));
        String areaName = firstNotBlank(parkName, touristVisitSessionMapper.selectAreaNameById(areaId));

        // 1. 查询是否有进行中的会话
        TouristVisitSession activeSession = touristVisitSessionMapper.selectActiveSessionByUserAndAreaId(userId, areaId);
        if (activeSession != null && activeSession.id != null) {
            // 复用已有会话，可更新 groupSize 等字段（可选）
            log.info("复用现有游览会话: visitId={}, userId={}, areaId={}", activeSession.id, userId, areaId);
            return buildVisitStartResponse(activeSession, areaCode, areaName, STATUS_ACTIVE_COMPAT);
        }

        // 2. 没有进行中的会话，创建新会话
        VisitStartRequest request = new VisitStartRequest();
        request.userId = userId;
        request.areaId = areaId;
        request.parkId = areaCode;
        request.parkName = areaName;
        request.groupSize = groupSize;
        request.travelType = travelType;
        request.visitPreference = visitPreference;
        request.longitude = longitude;
        request.latitude = latitude;
        request.startSource = "ai_guide";
        return startVisit(request);
    }
}
