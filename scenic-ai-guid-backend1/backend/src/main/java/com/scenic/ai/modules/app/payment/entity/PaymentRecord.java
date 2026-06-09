package com.scenic.ai.modules.app.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentRecord {

    public Long id;
    public String userId;
    public String wechatOpenid;
    public String consumptionType;
    public BigDecimal amount;
    public String paymentId;
    public String merchantName;
    public String locationId;
    public String status;
    public LocalDateTime payTime;
    public Long merchantId;
    public Long areaId;
    public Long visitId;
}
