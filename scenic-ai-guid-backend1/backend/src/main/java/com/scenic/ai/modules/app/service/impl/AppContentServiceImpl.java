package com.scenic.ai.modules.app.service.impl;

import com.scenic.ai.modules.app.dto.*;
import com.scenic.ai.modules.app.mapper.AppContentMapper;
import com.scenic.ai.modules.app.service.AppContentService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppContentServiceImpl implements AppContentService {

    private final AppContentMapper appContentMapper;

    public AppContentServiceImpl(AppContentMapper appContentMapper) {
        this.appContentMapper = appContentMapper;
    }

    @Override
    public List<ParkDto> getParks() {
        List<ParkDto> list = appContentMapper.selectParks();
        if (list == null) return Collections.emptyList();

        list.forEach(item -> {
            item.setTags(parseList(item.getTagsRaw()));
            item.setDigitalHumanConfig(buildParkDigitalHumanConfig(item.getId(), item.getName()));
        });

        return list;
    }

    @Override
    public List<ParkDto> getHotParks(Integer limit) {
        int safeLimit = limit == null ? 3 : limit;

        if (safeLimit <= 0) {
            safeLimit = 3;
        }

        if (safeLimit > 100) {
            safeLimit = 100;
        }

        List<ParkDto> list = appContentMapper.selectHotParks(safeLimit);
        if (list == null) return Collections.emptyList();

        list.forEach(item -> {
            item.setTags(parseList(item.getTagsRaw()));
            item.setDigitalHumanConfig(buildParkDigitalHumanConfig(item.getId(), item.getName()));
        });

        return list;
    }

    @Override
    public ParkDetailDto getParkDetail(String id) {
        ParkDetailDto detail = appContentMapper.selectParkDetail(id);

        if (detail != null) {
            detail.setTags(parseList(detail.getTagsRaw()));
            detail.setDigitalHumanConfig(buildParkDigitalHumanConfig(detail.getId(), detail.getName()));
        }

        return detail;
    }

    @Override
    public List<ScenicDto> getParkScenics(String parkId) {
        List<ScenicDto> list = appContentMapper.selectParkScenics(parkId);
        if (list == null) return Collections.emptyList();

        list.forEach(item -> {
            item.setTags(parseList(item.getTagsRaw()));
            item.setDigitalHumanConfig(
                    buildScenicDigitalHumanConfig(
                            item.getId(),
                            item.getName(),
                            item.getParkId()
                    )
            );
        });

        return list;
    }

    @Override
    public List<ParkSpotDto> getParkSpots(String parkId, String areaId) {
        String safeParkId = parkId == null ? "" : parkId.trim();
        String safeAreaId = areaId == null ? "" : areaId.trim();
        List<ParkSpotDto> list = appContentMapper.selectParkSpots(safeParkId, safeAreaId);
        return list == null ? Collections.emptyList() : list;
    }

    @Override
    public DigitalHumanConfigDto getDigitalHumanConfig(String parkId) {
        String safeParkId = parkId == null ? "" : parkId.trim();

        String defaultWelcomeText = "欢迎来到本景区，我是您的 AI 数字人导游。";

        if (safeParkId.isBlank()) {
            return normalizeDigitalHumanConfig(null, defaultWelcomeText);
        }

        DigitalHumanConfigDto dbConfig = selectParkDigitalHumanConfig(safeParkId);
        return normalizeDigitalHumanConfig(dbConfig, defaultWelcomeText);
    }

    @Override
    public ScenicDetailDto getScenicDetail(String id) {
        ScenicDetailDto detail = appContentMapper.selectScenicDetail(id);

        if (detail != null) {
            detail.setTags(parseList(detail.getTagsRaw()));
            detail.setHighlights(parseList(detail.getHighlightsRaw()));
            detail.setTips(parseList(detail.getTipsRaw()));

            detail.setDigitalHumanConfig(
                    buildScenicDigitalHumanConfig(
                            detail.getId(),
                            detail.getName(),
                            detail.getParkId()
                    )
            );
        }

        return detail;
    }

    @Override
    public List<NoticeDto> getNotices() {
        List<NoticeDto> list = appContentMapper.selectNotices();
        return list == null ? Collections.emptyList() : list;
    }

    private List<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(raw.split("[,，;；|]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * 构建景区数字人配置。
     *
     * 正式来源：
     * scenic_digital_human_config + digital_human_resource
     */
    private DigitalHumanConfigDto buildParkDigitalHumanConfig(String parkId, String parkName) {
        String targetName = firstNotBlank(parkName, "当前景区");

        String defaultWelcomeText =
                "欢迎来到“" + targetName + "”，我是你的 AI 数字人导游，现在为你开启智能讲解。";

        DigitalHumanConfigDto dbConfig = selectParkDigitalHumanConfig(parkId);

        return normalizeDigitalHumanConfig(dbConfig, defaultWelcomeText);
    }

    /**
     * 构建景点数字人配置。
     *
     * 当前没有景点单独配置表，所以景点继承所属景区的数字人配置。
     */
    private DigitalHumanConfigDto buildScenicDigitalHumanConfig(
            String scenicId,
            String scenicName,
            String parkId
    ) {
        String targetName = firstNotBlank(scenicName, "当前景点");

        String defaultWelcomeText =
                "欢迎来到“" + targetName + "”，我是你的 AI 数字人导游，现在为你讲解这个景点的历史文化和游玩亮点。";

        DigitalHumanConfigDto dbConfig = selectParkDigitalHumanConfig(parkId);

        return normalizeDigitalHumanConfig(dbConfig, defaultWelcomeText);
    }

    private DigitalHumanConfigDto selectParkDigitalHumanConfig(String parkId) {
        if (parkId == null || parkId.isBlank()) {
            return null;
        }

        return appContentMapper.selectParkDigitalHumanConfig(parkId.trim());
    }

    /**
     * 补全数字人配置默认值。
     *
     * 数据库没有配置时，仍然返回默认数字人，保证 APP 不会因为配置缺失打不开原生数字人。
     */
    private DigitalHumanConfigDto normalizeDigitalHumanConfig(
            DigitalHumanConfigDto source,
            String defaultWelcomeText
    ) {
        DigitalHumanConfigDto result = new DigitalHumanConfigDto();

        String finalAvatarId = firstNotBlank(
                source == null ? null : source.getAvatarId(),
                "guide_female_01"
        );

        if ("guide_default_01".equals(finalAvatarId)) {
            finalAvatarId = "guide_female_01";
        }

        result.setAvatarId(finalAvatarId);

        result.setAvatarName(firstNotBlank(
                source == null ? null : source.getAvatarName(),
                "灵灵"
        ));

        result.setClothesMode(firstNotBlank(
                source == null ? null : source.getClothesMode(),
                ""
        ));

        result.setVoiceId(firstNotBlank(
                source == null ? null : source.getVoiceId(),
                "zhitian_emo"
        ));

        result.setVoiceName(firstNotBlank(
                source == null ? null : source.getVoiceName(),
                "知甜"
        ));

        result.setWelcomeText(firstNotBlank(
                source == null ? null : source.getWelcomeText(),
                defaultWelcomeText
        ));

        result.setGender(safeString(source == null ? null : source.getGender()));
        result.setClothingName(safeString(source == null ? null : source.getClothingName()));

        result.setModelPath(firstNotBlank(
                source == null ? null : source.getModelPath(),
                finalAvatarId
        ));

        result.setPreviewImage(safeString(source == null ? null : source.getPreviewImage()));
        result.setRemark(safeString(source == null ? null : source.getRemark()));

        return result;
    }

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }
}
