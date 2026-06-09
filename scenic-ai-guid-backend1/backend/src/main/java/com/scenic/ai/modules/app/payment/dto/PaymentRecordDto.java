package com.scenic.ai.modules.app.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentRecordDto {

    public Long id;
    public String user_id;
    public String consumption_type;
    public BigDecimal amount;
    public String payment_id;
    public String merchant_name;
    public String location_id;
    public String status;
    public LocalDateTime pay_time;
    public Long merchant_id;
    public Long area_id;
    public Long visit_id;
}
