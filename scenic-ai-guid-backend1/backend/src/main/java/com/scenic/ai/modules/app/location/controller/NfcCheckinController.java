package com.scenic.ai.modules.app.location.controller;

import com.scenic.ai.common.result.ApiResponse;
import com.scenic.ai.modules.app.location.dto.NfcCheckinRequest;
import com.scenic.ai.modules.app.location.dto.NfcCheckinResponse;
import com.scenic.ai.modules.app.location.service.NfcCheckinService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/location")
@CrossOrigin
public class NfcCheckinController {

    private final NfcCheckinService nfcCheckinService;

    public NfcCheckinController(NfcCheckinService nfcCheckinService) {
        this.nfcCheckinService = nfcCheckinService;
    }

    @PostMapping("/nfc-checkin")
    public ApiResponse<NfcCheckinResponse> nfcCheckin(@RequestBody NfcCheckinRequest request) {
        try {
            if (request.getMarkerCode() == null || request.getMarkerCode().trim().isEmpty()) {
                return new ApiResponse<>(400, "marker_code不能为空", null);
            }

            NfcCheckinResponse response = nfcCheckinService.checkin(request);
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(400, e.getMessage(), null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponse<>(500, "NFC checkin失败", null);
        }
    }
}
