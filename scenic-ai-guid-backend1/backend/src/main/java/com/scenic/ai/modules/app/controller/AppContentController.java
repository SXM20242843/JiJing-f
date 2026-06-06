package com.scenic.ai.modules.app.controller;

import com.scenic.ai.common.result.ApiResponse;
import com.scenic.ai.modules.app.dto.*;
import com.scenic.ai.modules.app.service.AppContentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app")
public class AppContentController {

    private final AppContentService appContentService;

    public AppContentController(AppContentService appContentService) {
        this.appContentService = appContentService;
    }

    @GetMapping("/parks")
    public ApiResponse<List<ParkDto>> getParks() {
        return ApiResponse.success(appContentService.getParks());
    }

    @GetMapping("/parks/hot")
    public ApiResponse<List<ParkDto>> getHotParks(
            @RequestParam(defaultValue = "3") Integer limit
    ) {
        return ApiResponse.success(appContentService.getHotParks(limit));
    }
    @GetMapping("/parks/{id}")
    public ApiResponse<ParkDetailDto> getParkDetail(@PathVariable String id) {
        return ApiResponse.success(appContentService.getParkDetail(id));
    }

    @GetMapping("/parks/{id}/scenics")
    public ApiResponse<List<ScenicDto>> getParkScenics(@PathVariable String id) {
        return ApiResponse.success(appContentService.getParkScenics(id));
    }

    @GetMapping("/parks/{parkId}/spots")
    public ApiResponse<List<ParkSpotDto>> getParkSpots(
            @PathVariable String parkId,
            @RequestParam(value = "areaId", required = false) String areaId,
            @RequestParam(value = "area_id", required = false) String areaIdSnake
    ) {
        return ApiResponse.success(appContentService.getParkSpots(parkId, areaId != null ? areaId : areaIdSnake));
    }

    @GetMapping("/scenics/{id}")
    public ApiResponse<ScenicDetailDto> getScenicDetail(@PathVariable String id) {
        return ApiResponse.success(appContentService.getScenicDetail(id));
    }

    @GetMapping("/notices")
    public ApiResponse<List<NoticeDto>> getNotices() {
        return ApiResponse.success(appContentService.getNotices());
    }

    @GetMapping("/digital-human/config")
    public ApiResponse<DigitalHumanConfigDto> getDigitalHumanConfig(
            @RequestParam(value = "parkId", required = false) String parkId,
            @RequestParam(value = "park_id", required = false) String parkIdSnake
    ) {
        String finalParkId = parkId != null && !parkId.isBlank() ? parkId : parkIdSnake;
        return ApiResponse.success(appContentService.getDigitalHumanConfig(finalParkId));
    }
}
