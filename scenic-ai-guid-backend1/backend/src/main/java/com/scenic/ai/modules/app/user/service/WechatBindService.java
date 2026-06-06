package com.scenic.ai.modules.app.user.service;

import com.scenic.ai.common.config.WechatProperties;
import com.scenic.ai.modules.app.user.dto.WechatBindRequest;
import com.scenic.ai.modules.app.user.dto.WechatBindResponse;
import com.scenic.ai.modules.app.user.dto.WechatCodeSessionResponse;
import com.scenic.ai.modules.app.user.entity.WechatBindInfo;
import com.scenic.ai.modules.app.user.mapper.WechatBindMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;

@Service
public class WechatBindService {

    private final WechatBindMapper wechatBindMapper;
    private final WechatProperties wechatProperties;
    private final RestTemplate restTemplate;

    public WechatBindService(
            WechatBindMapper wechatBindMapper,
            WechatProperties wechatProperties,
            RestTemplate restTemplate
    ) {
        this.wechatBindMapper = wechatBindMapper;
        this.wechatProperties = wechatProperties;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public WechatBindResponse bindWechat(WechatBindRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        String userId = request.getUserIdText();
        String code = request.getCodeText();

        if (userId.isEmpty()) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        if (code.isEmpty()) {
            throw new IllegalArgumentException("code 不能为空");
        }

        if (wechatBindMapper.countUserByUserId(userId) <= 0) {
            throw new IllegalArgumentException("用户不存在");
        }

        WechatCodeSessionResponse session = exchangeCode(userId, code);
        if (session == null || isBlank(session.openid)) {
            throw new IllegalStateException("微信绑定失败，请稍后重试");
        }

        int rows = wechatBindMapper.updateWechatBind(
                userId,
                session.openid,
                blankToNull(session.unionid),
                null,
                null,
                LocalDateTime.now()
        );
        if (rows <= 0) {
            throw new IllegalStateException("微信绑定失败，请稍后重试");
        }

        WechatBindInfo bindInfo = wechatBindMapper.selectWechatBindInfo(userId);
        return toResponse(bindInfo);
    }

    private WechatCodeSessionResponse exchangeCode(String userId, String code) {
        if (Boolean.TRUE.equals(wechatProperties.getMockEnabled()) && code.startsWith("mock")) {
            WechatCodeSessionResponse response = new WechatCodeSessionResponse();
            String suffix = safeMockSuffix(code);
            response.openid = "mock_openid_" + suffix;
            response.unionid = "mock_unionid_" + safeMockSuffix(userId);
            return response;
        }

        if (isBlank(wechatProperties.getAppId()) || isBlank(wechatProperties.getAppSecret())) {
            throw new IllegalStateException("微信配置缺失");
        }

        String url = UriComponentsBuilder.fromHttpUrl(wechatProperties.getJscode2sessionUrl())
                .queryParam("appid", wechatProperties.getAppId())
                .queryParam("secret", wechatProperties.getAppSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        WechatCodeSessionResponse response = restTemplate.getForObject(url, WechatCodeSessionResponse.class);
        if (response == null) {
            throw new IllegalStateException("微信绑定失败，请稍后重试");
        }

        if (response.errcode != null && response.errcode != 0) {
            throw new IllegalStateException("微信绑定失败，请稍后重试");
        }

        return response;
    }

    private WechatBindResponse toResponse(WechatBindInfo bindInfo) {
        WechatBindResponse response = new WechatBindResponse();
        response.bindStatus = bindInfo != null && bindInfo.hasBindWechat != null && bindInfo.hasBindWechat == 1
                ? "bound"
                : "unbound";
        response.hasBindWechat = "bound".equals(response.bindStatus);
        response.wechatOpenid = bindInfo == null ? null : bindInfo.wechatOpenid;
        response.wechatUnionid = bindInfo == null ? null : bindInfo.wechatUnionid;
        response.wechatNickname = bindInfo == null ? null : bindInfo.wechatNickname;
        response.wechatAvatar = bindInfo == null ? null : bindInfo.wechatAvatar;
        return response;
    }

    private String safeMockSuffix(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9_]", "_");
        if (safe.length() > 48) {
            return safe.substring(0, 48);
        }
        return safe;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
