package com.scenic.ai.modules.app.location.dto;

import lombok.Data;

@Data
public class BehaviorBatchSyncResponse {

    private int received;
    private int success;
    private int failed;
}
