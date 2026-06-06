package com.scenic.ai.modules.app.service;

import com.scenic.ai.modules.app.dto.*;

import java.util.List;

public interface AppContentService {

    List<ParkDto> getParks();

    List<ParkDto> getHotParks(Integer limit);
    ParkDetailDto getParkDetail(String id);

    List<ScenicDto> getParkScenics(String parkId);

    List<ParkSpotDto> getParkSpots(String parkId, String areaId);

    DigitalHumanConfigDto getDigitalHumanConfig(String parkId);

    ScenicDetailDto getScenicDetail(String id);

    List<NoticeDto> getNotices();
}
