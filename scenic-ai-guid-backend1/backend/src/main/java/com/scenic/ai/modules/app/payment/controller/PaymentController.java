package com.scenic.ai.modules.app.payment.controller;

import com.scenic.ai.modules.app.payment.dto.PaymentRecordDto;
import com.scenic.ai.modules.app.payment.dto.PaymentShopInfoDto;
import com.scenic.ai.modules.app.payment.dto.PaymentSimulateRequest;
import com.scenic.ai.modules.app.payment.service.PaymentService;
import com.scenic.ai.modules.app.user.dto.ApiResult;
import com.scenic.ai.modules.app.user.service.AppUserService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/payment")
@CrossOrigin
public class PaymentController {

    private final PaymentService paymentService;
    private final AppUserService appUserService;

    public PaymentController(
            PaymentService paymentService,
            AppUserService appUserService
    ) {
        this.paymentService = paymentService;
        this.appUserService = appUserService;
    }

    @GetMapping("/shop/info")
    public ApiResult<PaymentShopInfoDto> shopInfo(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "shopCode", required = false) String shopCode,
            @RequestParam(value = "shop_code", required = false) String shopCodeSnake,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "facilityCode", required = false) String facilityCode,
            @RequestParam(value = "facility_code", required = false) String facilityCodeSnake,
            @RequestParam(value = "mapPoiId", required = false) String mapPoiId,
            @RequestParam(value = "map_poi_id", required = false) String mapPoiIdSnake,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "user_id", required = false) String userIdSnake
    ) {
        try {
            appUserService.resolveRequiredUserId(authorization, firstNotBlank(userId, userIdSnake));

            String finalShopCode = firstNotBlank(
                    shopCode,
                    shopCodeSnake,
                    code,
                    facilityCode,
                    facilityCodeSnake,
                    mapPoiId,
                    mapPoiIdSnake
            );

            return ApiResult.ok(paymentService.getShopInfo(finalShopCode));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("查询商户信息失败");
        }
    }

    @PostMapping("/simulate")
    public ApiResult<PaymentRecordDto> simulate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) PaymentSimulateRequest request
    ) {
        try {
            String providedUserId = request == null ? null : request.getUserIdText();
            String userId = appUserService.resolveRequiredUserId(authorization, providedUserId);
            return ApiResult.ok(paymentService.simulatePayment(request, userId));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("模拟支付失败");
        }
    }

    @GetMapping("/records")
    public ApiResult<List<PaymentRecordDto>> records(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "user_id", required = false) String userIdSnake
    ) {
        try {
            String resolvedUserId = appUserService.resolveRequiredUserId(
                    authorization,
                    firstNotBlank(userId, userIdSnake)
            );
            return ApiResult.ok(paymentService.listRecords(resolvedUserId));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResult.fail("查询消费记录失败");
        }
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