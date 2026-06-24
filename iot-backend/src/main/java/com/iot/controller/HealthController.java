package com.iot.controller;
// 系统健康检查控制器
import com.iot.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "iot-platform",
                "version", "1.0.0"
        ));
    }

    @GetMapping("/api/health/ready")
    public ApiResponse<Map<String, String>> readiness() {
        return ApiResponse.ok(Map.of("status", "READY"));
    }
}
