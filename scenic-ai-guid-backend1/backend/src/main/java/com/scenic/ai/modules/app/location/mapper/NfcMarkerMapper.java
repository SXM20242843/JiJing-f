package com.scenic.ai.modules.app.location.mapper;

import com.scenic.ai.modules.app.location.dto.NfcMarkerDto;
import org.apache.ibatis.annotations.*;

/**
 * scenic_nfc_marker 表 Mapper。
 */
@Mapper
public interface NfcMarkerMapper {

    /**
     * 根据 marker_code 查询启用的 NFC 点位。
     */
    @Select("""
        SELECT
          id,
          area_id          AS areaId,
          area_name        AS areaName,
          target_type      AS targetType,
          target_id        AS targetId,
          target_name      AS targetName,
          scene_code       AS sceneCode,
          marker_code      AS markerCode,
          marker_name      AS markerName,
          location_desc    AS locationDesc,
          guide_title      AS guideTitle,
          guide_summary    AS guideSummary,
          status
        FROM scenic_nfc_marker
        WHERE marker_code = #{markerCode}
          AND status = 1
        LIMIT 1
    """)
    @Results(id = "NfcMarkerMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "areaId", property = "areaId"),
            @Result(column = "areaName", property = "areaName"),
            @Result(column = "targetType", property = "targetType"),
            @Result(column = "targetId", property = "targetId"),
            @Result(column = "targetName", property = "targetName"),
            @Result(column = "sceneCode", property = "sceneCode"),
            @Result(column = "markerCode", property = "markerCode"),
            @Result(column = "markerName", property = "markerName"),
            @Result(column = "locationDesc", property = "locationDesc"),
            @Result(column = "guideTitle", property = "guideTitle"),
            @Result(column = "guideSummary", property = "guideSummary"),
            @Result(column = "status", property = "status")
    })
    NfcMarkerDto selectByMarkerCode(@Param("markerCode") String markerCode);

    /**
     * 更新 hit_count 和 last_hit_at。
     */
    @Update("""
        UPDATE scenic_nfc_marker
        SET hit_count   = hit_count + 1,
            last_hit_at = NOW()
        WHERE id = #{id}
    """)
    int incrementHitCount(@Param("id") Long id);
}
