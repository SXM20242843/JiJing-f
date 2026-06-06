package com.scenic.ai.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    private String appId;

    private String appSecret;

    private String jscode2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";

    private Boolean mockEnabled = false;
}
