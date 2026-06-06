package com.scenic.ai.modules.app.payment.dto;

import java.math.BigDecimal;

public class PaymentSimulateRequest {

    public String user_id;
    public String userId;

    public String wechat_openid;
    public String wechatOpenid;

    public String shop_code;
    public String shopCode;

    public Long merchant_id;
    public Long merchantId;

    public String merchant_name;
    public String merchantName;

    public String location_id;
    public String locationId;

    public String consumption_type;
    public String consumptionType;

    public BigDecimal amount;

    public String payment_id;
    public String paymentId;

    public String status;

    public String getUserIdText() {
        return firstNotBlank(user_id, userId);
    }

    public String getWechatOpenidText() {
        return firstNotBlank(wechat_openid, wechatOpenid);
    }

    public String getShopCodeText() {
        return firstNotBlank(shop_code, shopCode);
    }

    public Long getMerchantIdValue() {
        return merchant_id != null ? merchant_id : merchantId;
    }

    public String getMerchantNameText() {
        return firstNotBlank(merchant_name, merchantName);
    }

    public String getLocationIdText() {
        return firstNotBlank(location_id, locationId);
    }

    public String getConsumptionTypeText() {
        return firstNotBlank(consumption_type, consumptionType);
    }

    public String getPaymentIdText() {
        return firstNotBlank(payment_id, paymentId);
    }

    public String getStatusText() {
        return firstNotBlank(status);
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
