package com.scenic.ai.modules.chat.service;

import com.scenic.ai.modules.chat.dto.GuideChatRequest;

import java.math.BigDecimal;

public final class GuideVisitContextResolver {

    public static final String VISIT_STATUS_NOT_ARRIVED = "NOT_ARRIVED";
    public static final String VISIT_STATUS_IN_PARK = "IN_PARK";

    private GuideVisitContextResolver() {
    }

    public static VisitContext resolveVisitContext(GuideChatRequest request) {
        if (request == null) {
            return new VisitContext(VISIT_STATUS_NOT_ARRIVED, false, "", "");
        }
        return resolveVisitContext(
                request.getEffectiveUserId(),
                request.getAreaId(),
                request.getIsInsideArea(),
                request.getEffectiveCurrentSpotId(),
                request.getEffectiveCurrentSpotName(),
                request.getLongitude(),
                request.getLatitude(),
                request.getVisitId(),
                request.getVisitStatus(),
                request.getAreaCode(),
                request.getAreaName(),
                request.getEffectiveParkId(),
                request.getEffectiveParkName(),
                request.getMode()
        );
    }

    public static VisitContext resolveVisitContext(
            String userId,
            Long areaId,
            Boolean clientInsideArea,
            String simulatedSpotId,
            String simulatedSpotName,
            Double longitude,
            Double latitude
    ) {
        BigDecimal lng = longitude == null ? null : BigDecimal.valueOf(longitude);
        BigDecimal lat = latitude == null ? null : BigDecimal.valueOf(latitude);
        return resolveVisitContext(
                userId,
                areaId,
                clientInsideArea,
                simulatedSpotId,
                simulatedSpotName,
                lng,
                lat,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static VisitContext resolveVisitContext(
            String userId,
            Long areaId,
            Boolean clientInsideArea,
            String simulatedSpotId,
            String simulatedSpotName,
            BigDecimal longitude,
            BigDecimal latitude,
            String visitId,
            String visitStatus,
            String areaCode,
            String areaName,
            String parkId,
            String parkName,
            String mode
    ) {
        String normalizedStatus = normalizeVisitStatus(visitStatus);
        if (isPreArrivalMode(mode)) {
            return new VisitContext(VISIT_STATUS_NOT_ARRIVED, false, "", "");
        }
        boolean explicitStatus = hasText(visitStatus);
        boolean hasLocation = hasValidCoordinate(longitude, latitude);
        boolean hasSpot = isUsableCurrentSpot(simulatedSpotId, simulatedSpotName, areaId, areaCode, areaName, parkId, parkName);

        if (explicitStatus && VISIT_STATUS_NOT_ARRIVED.equals(normalizedStatus)) {
            return new VisitContext(VISIT_STATUS_NOT_ARRIVED, false, "", "");
        }
        if (Boolean.FALSE.equals(clientInsideArea)) {
            return new VisitContext(VISIT_STATUS_NOT_ARRIVED, false, "", "");
        }

        if (VISIT_STATUS_IN_PARK.equals(normalizedStatus)) {
            if (!isOnsiteMode(mode) && !Boolean.TRUE.equals(clientInsideArea)) {
                return new VisitContext(VISIT_STATUS_NOT_ARRIVED, false, "", "");
            }
            if (isLegacyActiveVisitStatus(visitStatus)
                    && !Boolean.TRUE.equals(clientInsideArea)
                    && !hasLocation
                    && !hasSpot) {
                return new VisitContext(VISIT_STATUS_NOT_ARRIVED, false, "", "");
            }
            return new VisitContext(
                    VISIT_STATUS_IN_PARK,
                    true,
                    hasSpot ? safeTrim(simulatedSpotId) : "",
                    hasSpot ? safeTrim(simulatedSpotName) : ""
            );
        }

        if (Boolean.TRUE.equals(clientInsideArea)) {
            return new VisitContext(
                    VISIT_STATUS_IN_PARK,
                    true,
                    hasSpot ? safeTrim(simulatedSpotId) : "",
                    hasSpot ? safeTrim(simulatedSpotName) : ""
            );
        }

        // A stale visit_id alone is not enough to put the user back in park.
        return new VisitContext(VISIT_STATUS_NOT_ARRIVED, false, "", "");
    }

    private static boolean isPreArrivalMode(String mode) {
        String text = safeTrim(mode).toLowerCase();
        return "normal".equals(text)
                || "scenic_explain".equals(text)
                || "spot_explain".equals(text)
                || "route_planning".equals(text);
    }

    private static boolean isOnsiteMode(String mode) {
        String text = safeTrim(mode).toLowerCase();
        return "onsite".equals(text)
                || "onsite_guide".equals(text)
                || "live_onsite".equals(text);
    }

    public static String normalizeVisitStatus(String value) {
        String text = safeTrim(value);
        if (text.isEmpty()) {
            return VISIT_STATUS_NOT_ARRIVED;
        }
        String upper = text.toUpperCase();
        if (VISIT_STATUS_IN_PARK.equals(upper)
                || "IN_AREA".equals(upper)
                || "ARRIVED".equals(upper)
                || "VISITING".equals(upper)
                || "ONGOING".equals(upper)
                || "ACTIVE".equals(upper)
                || "IN_PROGRESS".equals(upper)
                || "ONSITE".equals(upper)) {
            return VISIT_STATUS_IN_PARK;
        }
        if (VISIT_STATUS_NOT_ARRIVED.equals(upper)
                || "NOT_IN_AREA".equals(upper)
                || "OUT_OF_PARK".equals(upper)
                || "OUTSIDE".equals(upper)) {
            return VISIT_STATUS_NOT_ARRIVED;
        }
        return upper;
    }

    private static boolean isLegacyActiveVisitStatus(String value) {
        String upper = safeTrim(value).toUpperCase();
        return "VISITING".equals(upper)
                || "ONGOING".equals(upper)
                || "ACTIVE".equals(upper)
                || "IN_PROGRESS".equals(upper);
    }

    public static boolean hasRouteIntentText(String question) {
        String text = safeTrim(question);
        if (text.isEmpty()) {
            return false;
        }
        return text.contains("推荐路线")
                || text.contains("怎么逛")
                || text.contains("接下来去哪")
                || text.contains("规划行程")
                || text.contains("从当前位置出发")
                || text.contains("帮我安排游览顺序")
                || text.contains("游览路线")
                || text.contains("先去哪后去哪")
                || text.contains("先去哪")
                || text.contains("后去哪");
    }

    public static boolean isUsableCurrentSpot(
            String spotId,
            String spotName,
            Long areaId,
            String areaCode,
            String areaName,
            String parkId,
            String parkName
    ) {
        String id = safeTrim(spotId);
        String name = safeTrim(spotName);
        if (id.isEmpty() || name.isEmpty()) {
            return false;
        }
        String upperId = id.toUpperCase();
        if ("PARK_ENTRANCE".equals(upperId) || "SCENIC_ENTRANCE".equals(upperId) || upperId.startsWith("AREA_")) {
            return false;
        }
        if (areaId != null && id.equals(String.valueOf(areaId))) {
            return false;
        }
        if (equalsIgnoreCase(id, areaCode) || equalsIgnoreCase(id, parkId)) {
            return false;
        }
        if (equalsIgnoreCase(name, areaName) || equalsIgnoreCase(name, parkName)) {
            return false;
        }
        return !name.contains("入口/中心点") && !"景区入口".equals(name);
    }

    public static boolean hasValidCoordinate(BigDecimal longitude, BigDecimal latitude) {
        if (longitude == null || latitude == null) {
            return false;
        }
        return latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                && latitude.compareTo(BigDecimal.valueOf(90)) <= 0
                && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return hasText(left) && hasText(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class VisitContext {
        private final String visitStatus;
        private final boolean inPark;
        private final String currentSpotId;
        private final String currentSpotName;

        private VisitContext(String visitStatus, boolean inPark, String currentSpotId, String currentSpotName) {
            this.visitStatus = visitStatus;
            this.inPark = inPark;
            this.currentSpotId = currentSpotId == null ? "" : currentSpotId;
            this.currentSpotName = currentSpotName == null ? "" : currentSpotName;
        }

        public String getVisitStatus() {
            return visitStatus;
        }

        public boolean isInPark() {
            return inPark;
        }

        public String getCurrentSpotId() {
            return currentSpotId;
        }

        public String getCurrentSpotName() {
            return currentSpotName;
        }
    }
}
