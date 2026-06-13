package com.scenic.ai.modules.app.offline.service;

import com.scenic.ai.modules.app.offline.dto.OfflinePackageDto;
import com.scenic.ai.modules.app.offline.mapper.OfflinePackageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OfflinePackageService {

    private final OfflinePackageMapper offlinePackageMapper;

    public OfflinePackageService(OfflinePackageMapper offlinePackageMapper) {
        this.offlinePackageMapper = offlinePackageMapper;
    }

    public OfflinePackageDto getLatest(Long areaId) {
        if (areaId == null) {
            throw new IllegalArgumentException("areaId 不能为空");
        }

        log.info("[OfflinePackage] latest request areaId={}", areaId);
        OfflinePackageDto latest = offlinePackageMapper.selectLatestPublished(areaId);
        if (latest == null) {
            log.info("[OfflinePackage] latest not found areaId={}", areaId);
            return null;
        }

        log.info("[OfflinePackage] latest found version={}, url={}",
                latest.getPackageVersion(), latest.getPackageUrl());
        return latest;
    }
}
