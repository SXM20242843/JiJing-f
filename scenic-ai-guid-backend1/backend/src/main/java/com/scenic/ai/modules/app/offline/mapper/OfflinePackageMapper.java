package com.scenic.ai.modules.app.offline.mapper;

import com.scenic.ai.modules.app.offline.dto.OfflinePackageDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OfflinePackageMapper {

    @Select("""
        SELECT
          area_id           AS areaId,
          area_name         AS areaName,
          package_version   AS packageVersion,
          package_url       AS packageUrl,
          package_size      AS packageSize,
          content_hash      AS contentHash,
          includes_audio    AS includesAudio,
          includes_map      AS includesMap,
          spot_count        AS spotCount,
          nfc_marker_count  AS nfcMarkerCount,
          route_count       AS routeCount,
          faq_count         AS faqCount,
          DATE_FORMAT(published_at, '%Y-%m-%d %H:%i:%s') AS publishedAt
        FROM scenic_offline_package
        WHERE area_id = #{areaId}
          AND status = 1
        ORDER BY published_at DESC, updated_at DESC, id DESC
        LIMIT 1
    """)
    OfflinePackageDto selectLatestPublished(@Param("areaId") Long areaId);
}
