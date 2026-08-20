package com.tradex.auth.controller;

import com.tradex.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> healthCheck() {
        Map<String, String> status = Map.of(
            "status", "UP",
            "service", "TradeX Order Management System"
        );
        return ResponseEntity.ok(ApiResponse.success("System operational", status));
    }
}
