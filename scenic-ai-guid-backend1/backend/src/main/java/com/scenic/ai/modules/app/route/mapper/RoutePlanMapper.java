package com.scenic.ai.modules.app.route.mapper;

import com.scenic.ai.modules.app.route.entity.TouristRoutePlan;
import com.scenic.ai.modules.app.route.entity.TouristRoutePlanNode;
import com.scenic.ai.modules.app.route.dto.RouteAreaInfo;
import com.scenic.ai.modules.app.route.dto.RouteSpotInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoutePlanMapper {

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
        FROM scenic_area
        WHERE deleted = 0
          AND status = 1
          AND (
            area_code = #{parkId}
            OR CAST(id AS CHAR) = #{parkId}
          )
        LIMIT 1
    """)
    Long selectAreaIdByParkId(@Param("parkId") String parkId);

    @Select("""
        SELECT
          id AS area_id,
          area_code,
          area_name,
          longitude,
          latitude
        FROM scenic_area
        WHERE id = #{areaId}
          AND deleted = 0
          AND status = 1
        LIMIT 1
    """)
    @Results(id = "RouteAreaInfoMap", value = {
            @Result(column = "area_id", property = "areaId"),
            @Result(column = "area_code", property = "areaCode"),
            @Result(column = "area_name", property = "areaName"),
            @Result(column = "longitude", property = "longitude"),
            @Result(column = "latitude", property = "latitude")
    })
    RouteAreaInfo selectAreaInfoById(@Param("areaId") Long areaId);

    @Select("""
        SELECT
          s.id AS spot_id,
          s.area_id,
          a.area_code,
          a.area_name,
          s.scene_code,
          s.name AS spot_name,
          s.intro,
          s.tags,
          s.longitude,
          s.latitude,
          s.recommended_duration_min,
          s.sort_order,
          COALESCE(h.heat_score, 0) AS heat_score
        FROM scenic_spot s
          INNER JOIN scenic_area a ON a.id = s.area_id
          LEFT JOIN (
            SELECT spot_id, COUNT(1) AS heat_score
            FROM tourist_behavior_event
            WHERE spot_id IS NOT NULL
            GROUP BY spot_id
          ) h ON h.spot_id = s.id
        WHERE s.area_id = #{areaId}
          AND s.status = 1
          AND COALESCE(s.deleted, 0) = 0
          AND a.status = 1
          AND a.deleted = 0
          AND s.latitude IS NOT NULL
          AND s.longitude IS NOT NULL
        ORDER BY COALESCE(s.sort_order, 0) ASC, COALESCE(h.heat_score, 0) DESC, s.id ASC
    """)
    @Results(id = "RouteSpotInfoMap", value = {
            @Result(column = "spot_id", property = "spotId"),
            @Result(column = "area_id", property = "areaId"),
            @Result(column = "area_code", property = "areaCode"),
            @Result(column = "area_name", property = "areaName"),
            @Result(column = "scene_code", property = "sceneCode"),
            @Result(column = "spot_name", property = "spotName"),
            @Result(column = "intro", property = "intro"),
            @Result(column = "tags", property = "tags"),
            @Result(column = "longitude", property = "longitude"),
            @Result(column = "latitude", property = "latitude"),
            @Result(column = "recommended_duration_min", property = "recommendedDurationMin"),
            @Result(column = "sort_order", property = "sortOrder")
    })
    List<RouteSpotInfo> selectAvailableSpotsByAreaId(@Param("areaId") Long areaId);

    @Insert("""
        INSERT INTO tourist_route_plan (
          plan_no,
          user_id,
          session_id,
          visit_id,
          area_id,
          route_name,
          start_longitude,
          start_latitude,
          end_longitude,
          end_latitude,
          total_distance_m,
          estimated_duration_min,
          preference_snapshot,
          reason,
          raw_response_json,
          plan_status
        ) VALUES (
          #{planNo},
          #{userId},
          #{sessionId},
          #{visitId},
          #{areaId},
          #{routeName},
          #{startLongitude},
          #{startLatitude},
          #{endLongitude},
          #{endLatitude},
          #{totalDistanceM},
          #{estimatedDurationMin},
          CAST(#{preferenceSnapshot} AS JSON),
          #{reason},
          CAST(#{rawResponseJson} AS JSON),
          #{planStatus}
        )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertRoutePlan(TouristRoutePlan plan);

    @Insert("""
        <script>
        INSERT INTO tourist_route_plan_node (
          plan_id,
          node_type,
          spot_id,
          facility_id,
          node_name,
          longitude,
          latitude,
          sort_order,
          distance_from_prev_m,
          estimated_walk_min,
          recommended_stay_min,
          guide_text
        ) VALUES
        <foreach collection="nodes" item="node" separator=",">
        (
          #{node.planId},
          #{node.nodeType},
          #{node.spotId},
          #{node.facilityId},
          #{node.nodeName},
          #{node.longitude},
          #{node.latitude},
          #{node.sortOrder},
          #{node.distanceFromPrevM},
          #{node.estimatedWalkMin},
          #{node.recommendedStayMin},
          #{node.guideText}
        )
        </foreach>
        </script>
    """)
    int batchInsertRoutePlanNodes(@Param("nodes") List<TouristRoutePlanNode> nodes);
}
