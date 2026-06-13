package com.scenic.ai.modules.app.offline.controller;

import com.scenic.ai.common.result.ApiResponse;
import com.scenic.ai.modules.app.offline.dto.OfflinePackageDto;
import com.scenic.ai.modules.app.offline.service.OfflinePackageService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/offline-package")
@CrossOrigin
public class OfflinePackageController {

    private final OfflinePackageService offlinePackageService;

    public OfflinePackageController(OfflinePackageService offlinePackageService) {
        this.offlinePackageService = offlinePackageService;
    }

    @GetMapping("/latest")
    public ApiResponse<OfflinePackageDto> latest(@RequestParam("areaId") Long areaId) {
        try {
            return ApiResponse.success(offlinePackageService.getLatest(areaId));
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(400, e.getMessage(), null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.fail("查询离线包失败");
        }
    }
}
