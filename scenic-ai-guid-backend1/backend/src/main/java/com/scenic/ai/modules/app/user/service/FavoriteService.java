package com.scenic.ai.modules.app.user.service;

import com.scenic.ai.modules.app.user.dto.FavoriteItemDto;
import com.scenic.ai.modules.app.user.dto.FavoriteRequest;
import com.scenic.ai.modules.app.user.dto.BehaviorEventRequest;
import com.scenic.ai.modules.app.user.mapper.FavoriteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final BehaviorEventService behaviorEventService;

    public FavoriteService(FavoriteMapper favoriteMapper, BehaviorEventService behaviorEventService) {
        this.favoriteMapper = favoriteMapper;
        this.behaviorEventService = behaviorEventService;
    }

    @Transactional
    public boolean addFavorite(FavoriteRequest request) {
        return addFavorite(request, null);
    }

    @Transactional
    public boolean addFavorite(FavoriteRequest request, String requiredUserId) {
        ResolvedFavorite favorite = resolveFavorite(request, requiredUserId);

        favoriteMapper.addFavorite(
                favorite.userId,
                favorite.targetType,
                favorite.targetId
        );

        behaviorEventService.addBehaviorEvent(buildFavoriteEventRequest(request, favorite), favorite.userId);

        return true;
    }

    public boolean removeFavorite(FavoriteRequest request) {
        return removeFavorite(request, null);
    }

    public boolean removeFavorite(FavoriteRequest request, String requiredUserId) {
        ResolvedFavorite favorite = resolveFavorite(request, requiredUserId);

        favoriteMapper.removeFavorite(
                favorite.userId,
                favorite.targetType,
                favorite.targetId
        );

        return true;
    }

    public boolean isFavorite(FavoriteRequest request) {
        ResolvedFavorite favorite = resolveFavorite(request);

        return favoriteMapper.countFavorite(
                favorite.userId,
                favorite.targetType,
                favorite.targetId
        ) > 0;
    }

    public List<FavoriteItemDto> listFavorites(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("请先登录");
        }

        return favoriteMapper.selectFavoriteList(userId.trim());
    }

    private ResolvedFavorite resolveFavorite(FavoriteRequest request) {
        return resolveFavorite(request, null);
    }

    private ResolvedFavorite resolveFavorite(FavoriteRequest request, String requiredUserId) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        String userId = firstNotBlank(requiredUserId, request.getUserIdText());
        String targetType = request.getTargetTypeText();
        Long targetId = request.getTargetIdValue();

        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("请先登录");
        }

        if (userId.startsWith("visitor_")) {
            throw new IllegalArgumentException("游客模式暂不支持云端收藏，请先登录");
        }

        if (targetType == null || targetType.trim().isEmpty()) {
            throw new IllegalArgumentException("收藏类型不能为空");
        }

        if (!"SPOT".equals(targetType)
                && !"AREA".equals(targetType)
                && !"FACILITY".equals(targetType)
                && !"ROUTE".equals(targetType)) {
            throw new IllegalArgumentException("收藏类型仅支持 AREA / SPOT / FACILITY / ROUTE");
        }

        if (targetId == null) {
            if ("SPOT".equals(targetType)) {
                String sceneCode = request.getSceneCodeText();
                if (sceneCode.isEmpty()) {
                    throw new IllegalArgumentException("缺少 scene_code");
                }

                targetId = favoriteMapper.selectSpotIdBySceneCode(sceneCode);

                if (targetId == null) {
                    throw new IllegalArgumentException("未找到对应景点：" + sceneCode);
                }
            }

            if ("AREA".equals(targetType)) {
                String areaCode = request.getAreaCodeText();
                if (areaCode.isEmpty()) {
                    throw new IllegalArgumentException("缺少 area_code");
                }

                targetId = favoriteMapper.selectAreaIdByAreaCode(areaCode);

                if (targetId == null) {
                    throw new IllegalArgumentException("未找到对应景区：" + areaCode);
                }
            }
        }

        ResolvedFavorite favorite = new ResolvedFavorite();
        favorite.userId = userId;
        favorite.targetType = targetType;
        favorite.targetId = targetId;

        return favorite;
    }

    private BehaviorEventRequest buildFavoriteEventRequest(FavoriteRequest request, ResolvedFavorite favorite) {
        BehaviorEventRequest event = new BehaviorEventRequest();
        event.userId = favorite.userId;
        event.sessionId = request.getSessionIdText();
        event.visitId = request.getVisitIdValue();
        event.entityType = favorite.targetType;
        event.entityId = String.valueOf(favorite.targetId);
        event.eventType = "FAVORITE";
        event.eventName = "收藏";
        event.sourcePage = request.getSourcePageText();
        event.content = request.getContentText();

        if ("AREA".equals(favorite.targetType)) {
            event.areaId = favorite.targetId;
        } else if ("SPOT".equals(favorite.targetType)) {
            event.spotId = favorite.targetId;
            event.areaId = favoriteMapper.selectAreaIdBySpotId(favorite.targetId);
        }

        return event;
    }

    private static class ResolvedFavorite {
        String userId;
        String targetType;
        Long targetId;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }
}
