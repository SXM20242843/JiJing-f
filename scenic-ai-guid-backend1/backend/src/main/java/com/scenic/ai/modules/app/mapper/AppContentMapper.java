package com.scenic.ai.modules.app.mapper;

import com.scenic.ai.modules.app.dto.*;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AppContentMapper {

    List<ParkDto> selectParks();

    List<ParkDto> selectHotParks(@Param("limit") Integer limit);

    ParkDetailDto selectParkDetail(@Param("id") String id);

    List<ScenicDto> selectParkScenics(@Param("parkId") String parkId);

    List<ParkSpotDto> selectParkSpots(
            @Param("parkId") String parkId,
            @Param("areaId") String areaId
    );

    ScenicDetailDto selectScenicDetail(@Param("id") String id);

    List<NoticeDto> selectNotices();

    /**
     * 查询某个景区当前启用的数字人配置。
     *
     * parkId 使用 scenic_area.area_code，例如 AREA_0001。
     */
    DigitalHumanConfigDto selectParkDigitalHumanConfig(@Param("parkId") String parkId);
}
