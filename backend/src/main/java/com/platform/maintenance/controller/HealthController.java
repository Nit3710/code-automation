package com.platform.maintenance.controller;

import com.platform.maintenance.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping({"/health", "/ping"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.ok("System healthy", Map.of(
                "status", "UP",
                "service", "AutoMaintain Platform Backend",
                "timestamp", System.currentTimeMillis()
        )));
    }
}
