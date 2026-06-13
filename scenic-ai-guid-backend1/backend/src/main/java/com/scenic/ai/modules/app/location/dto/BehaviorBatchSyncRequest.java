package com.scenic.ai.modules.app.location.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class BehaviorBatchSyncRequest {

    @JsonAlias({"user_id"})
    private String userId;

    private List<BehaviorEventItem> events;
}
