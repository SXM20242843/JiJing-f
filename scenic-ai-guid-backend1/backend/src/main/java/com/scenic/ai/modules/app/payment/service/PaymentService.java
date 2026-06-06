package com.scenic.ai.modules.app.payment.service;

import com.scenic.ai.modules.app.payment.dto.PaymentRecordDto;
import com.scenic.ai.modules.app.payment.dto.PaymentShopInfoDto;
import com.scenic.ai.modules.app.payment.dto.PaymentSimulateRequest;
import com.scenic.ai.modules.app.payment.entity.PaymentRecord;
import com.scenic.ai.modules.app.payment.mapper.PaymentMapper;
import com.scenic.ai.modules.app.user.dto.BehaviorEventRequest;
import com.scenic.ai.modules.app.user.service.BehaviorEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final Set<String> CONSUMPTION_TYPES = Set.of(
            "ticket",
            "food",
            "shopping",
            "transport",
            "entertainment",
            "accommodation"
    );

    private static final DateTimeFormatter PAYMENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PaymentMapper paymentMapper;
    private final BehaviorEventService behaviorEventService;

    public PaymentService(
            PaymentMapper paymentMapper,
            BehaviorEventService behaviorEventService
    ) {
        this.paymentMapper = paymentMapper;
        this.behaviorEventService = behaviorEventService;
    }

    public PaymentShopInfoDto getShopInfo(String shopCode) {
        String normalizedShopCode = trimToEmpty(shopCode);
        if (normalizedShopCode.isEmpty()) {
            throw new IllegalArgumentException("shopCode 不能为空");
        }

        PaymentShopInfoDto info = paymentMapper.selectShopInfoByShopCode(normalizedShopCode);
        if (info != null) {
            info.shopCode = normalizedShopCode;
            info.consumptionType = normalizeConsumptionType(info.consumptionType);
            return info;
        }

        PaymentShopInfoDto fallback = new PaymentShopInfoDto();
        fallback.shopCode = normalizedShopCode;
        fallback.merchantName = "扫码商户";
        fallback.locationId = normalizedShopCode;
        fallback.consumptionType = "entertainment";
        return fallback;
    }

    @Transactional
    public PaymentRecordDto simulatePayment(PaymentSimulateRequest request, String userId) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        if (request.amount == null || request.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount 必须大于 0");
        }

        String shopCode = request.getShopCodeText();
        PaymentShopInfoDto shopInfo = shopCode.isEmpty() ? null : getShopInfo(shopCode);

        Long merchantId = firstLong(
                shopInfo == null ? null : shopInfo.merchantId,
                request.getMerchantIdValue()
        );

        String merchantName = firstNotBlank(
                shopInfo == null ? "" : shopInfo.merchantName,
                request.getMerchantNameText(),
                "扫码商户"
        );

        String locationId = firstNotBlank(
                shopInfo == null ? "" : shopInfo.locationId,
                request.getLocationIdText(),
                shopCode
        );

        String consumptionType = normalizeConsumptionType(firstNotBlank(
                shopInfo == null ? "" : shopInfo.consumptionType,
                request.getConsumptionTypeText(),
                "entertainment"
        ));

        String paymentId = firstNotBlank(request.getPaymentIdText(), generatePaymentId());
        String status = firstNotBlank(request.getStatusText(), "completed");

        PaymentRecord record = new PaymentRecord();
        record.userId = userId;
        record.wechatOpenid = emptyToNull(request.getWechatOpenidText());
        record.consumptionType = consumptionType;
        record.amount = request.amount;
        record.paymentId = paymentId;
        record.merchantName = merchantName;
        record.locationId = locationId;
        record.status = status;
        record.payTime = LocalDateTime.now();
        record.merchantId = merchantId;

        int rows = paymentMapper.insertPaymentRecord(record);
        if (rows <= 0 || record.id == null) {
            throw new IllegalStateException("模拟支付入库失败");
        }

        try {
            recordConsumeBehavior(record, shopCode, userId);
        } catch (Exception e) {
            log.warn("记录扫码消费行为失败，不影响支付记录。paymentId=" + record.paymentId, e);
        }

        return toDto(record);
    }

    public List<PaymentRecordDto> listRecords(String userId) {
        return paymentMapper.selectRecordsByUserId(userId);
    }

    private void recordConsumeBehavior(PaymentRecord record, String shopCode, String userId) {
        BehaviorEventRequest event = new BehaviorEventRequest();
        event.userId = userId;
        event.facilityId = record.merchantId;
        event.entityType = record.merchantId == null ? "MERCHANT" : "FACILITY";
        event.entityId = record.merchantId == null
                ? firstNotBlank(shopCode, record.locationId)
                : String.valueOf(record.merchantId);
        event.eventType = "CONSUME";
        event.eventName = "扫码消费";
        event.sourcePage = "payment_simulate";
        event.content = record.merchantName;

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("amount", record.amount);
        extra.put("payment_id", record.paymentId);
        extra.put("merchant_name", record.merchantName);
        extra.put("consumption_type", record.consumptionType);
        extra.put("shop_code", shopCode);
        extra.put("location_id", record.locationId);
        event.extra = extra;

        behaviorEventService.addBehaviorEvent(event, userId);
    }

    private PaymentRecordDto toDto(PaymentRecord record) {
        PaymentRecordDto dto = new PaymentRecordDto();
        dto.id = record.id;
        dto.user_id = record.userId;
        dto.consumption_type = record.consumptionType;
        dto.amount = record.amount;
        dto.payment_id = record.paymentId;
        dto.merchant_name = record.merchantName;
        dto.location_id = record.locationId;
        dto.status = record.status;
        dto.pay_time = record.payTime;
        dto.merchant_id = record.merchantId;
        return dto;
    }

    private String normalizeConsumptionType(String value) {
        String normalized = trimToEmpty(value).toLowerCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            return "entertainment";
        }

        switch (normalized) {
            case "门票":
            case "票务":
            case "售票":
            case "ticket_office":
            case "ticket-office":
                return "ticket";

            case "餐饮":
            case "餐厅":
            case "饭店":
            case "美食":
            case "素斋":
            case "restaurant":
            case "dining":
                return "food";

            case "购物":
            case "商店":
            case "文创":
            case "零售":
            case "store":
            case "shop":
                return "shopping";

            case "交通":
            case "停车":
            case "停车场":
            case "观光车":
            case "摆渡车":
            case "parking":
            case "bus":
                return "transport";

            case "酒店":
            case "住宿":
            case "客栈":
            case "民宿":
            case "hotel":
            case "accommodation":
                return "accommodation";

            case "娱乐":
            case "体验":
            case "体验区":
            case "entertainment":
                return "entertainment";

            default:
                break;
        }

        if (!CONSUMPTION_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "consumption_type 必须是 ticket/food/shopping/transport/entertainment/accommodation"
            );
        }

        return normalized;
    }

    private String generatePaymentId() {
        String random = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);
        return "SIM" + LocalDateTime.now().format(PAYMENT_TIME_FORMATTER) + random;
    }

    private Long firstLong(Long... values) {
        if (values == null) {
            return null;
        }

        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToNull(String value) {
        String trimmed = trimToEmpty(value);
        return trimmed.isEmpty() ? null : trimmed;
    }
}