package com.scenic.ai.modules.app.user.mapper;

import com.scenic.ai.modules.app.user.dto.FavoriteItemDto;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FavoriteMapper {

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
        SELECT area_id
        FROM scenic_spot
        WHERE id = #{spotId}
          AND deleted = 0
        LIMIT 1
    """)
    Long selectAreaIdBySpotId(@Param("spotId") Long spotId);

    @Insert("""
        INSERT INTO tourist_favorite (
          user_id,
          target_type,
          target_id,
          status
        ) VALUES (
          #{userId},
          #{targetType},
          #{targetId},
          1
        )
        ON DUPLICATE KEY UPDATE
          status = 1,
          updated_at = NOW()
    """)
    int addFavorite(
            @Param("userId") String userId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId
    );

    @Update("""
        UPDATE tourist_favorite
        SET status = 0,
            updated_at = NOW()
        WHERE user_id = #{userId}
          AND target_type = #{targetType}
          AND target_id = #{targetId}
    """)
    int removeFavorite(
            @Param("userId") String userId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId
    );

    @Select("""
        SELECT COUNT(1)
        FROM tourist_favorite
        WHERE user_id = #{userId}
          AND target_type = #{targetType}
          AND target_id = #{targetId}
          AND status = 1
    """)
    int countFavorite(
            @Param("userId") String userId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId
    );

    @Select("""
        SELECT
          f.id,
          f.user_id,
          f.target_type,
          f.target_id,
          f.status,
          CASE
            WHEN f.target_type = 'SPOT' THEN s.name
            WHEN f.target_type = 'AREA' THEN a.area_name
            ELSE ''
          END AS target_name,
          CASE
            WHEN f.target_type = 'SPOT' THEN s.intro
            WHEN f.target_type = 'AREA' THEN a.intro
            ELSE ''
          END AS intro,
          CASE
            WHEN f.target_type = 'SPOT' THEN s.image_url
            WHEN f.target_type = 'AREA' THEN a.image_url
            ELSE ''
          END AS image_url,
          a.area_code AS area_code,
          s.scene_code AS scene_code,
          f.created_at,
          f.updated_at
        FROM tourist_favorite f
        LEFT JOIN scenic_spot s
          ON f.target_type = 'SPOT'
         AND f.target_id = s.id
         AND s.deleted = 0
        LEFT JOIN scenic_area a
          ON (
            (f.target_type = 'AREA' AND f.target_id = a.id)
            OR
            (f.target_type = 'SPOT' AND s.area_id = a.id)
          )
         AND a.deleted = 0
        WHERE f.user_id = #{userId}
          AND f.status = 1
        ORDER BY f.updated_at DESC
    """)
    @Results(id = "FavoriteItemMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "user_id"),
            @Result(column = "target_type", property = "target_type"),
            @Result(column = "target_id", property = "target_id"),
            @Result(column = "status", property = "status"),
            @Result(column = "target_name", property = "target_name"),
            @Result(column = "intro", property = "intro"),
            @Result(column = "image_url", property = "image_url"),
            @Result(column = "area_code", property = "area_code"),
            @Result(column = "scene_code", property = "scene_code"),
            @Result(column = "created_at", property = "created_at"),
            @Result(column = "updated_at", property = "updated_at")
    })
    List<FavoriteItemDto> selectFavoriteList(@Param("userId") String userId);
}
