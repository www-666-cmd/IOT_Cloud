package com.iot.controller;

import com.iot.config.SecurityUtils;
import com.iot.dto.ApiResponse;
import com.iot.model.CommandLog;
import com.iot.repository.CommandLogRepository;
import com.iot.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;
    private final CommandLogRepository commandLogRepository;
    private final SecurityUtils securityUtils;

    /**
     * Start data upload simulation (devices -> platform)
     */
    @PostMapping("/upload/start")
    public ApiResponse<Map<String, Object>> startUpload(@RequestParam(defaultValue = "5") int interval) {
        simulationService.startDataSimulation(interval);
        return ApiResponse.ok("数据上发模拟已启动", Map.of("intervalSeconds", interval));
    }

    /**
     * Stop data upload simulation
     */
    @PostMapping("/upload/stop")
    public ApiResponse<Void> stopUpload() {
        simulationService.stopDataSimulation();
        return ApiResponse.ok("数据上发模拟已停止", null);
    }

    /**
     * Start command delivery simulation (platform -> devices)
     */
    @PostMapping("/delivery/start")
    public ApiResponse<Map<String, Object>> startDelivery(@RequestParam(defaultValue = "10") int interval) {
        simulationService.startCommandSimulation(interval);
        return ApiResponse.ok("命令下报模拟已启动", Map.of("intervalSeconds", interval));
    }

    /**
     * Stop command delivery simulation
     */
    @PostMapping("/delivery/stop")
    public ApiResponse<Void> stopDelivery() {
        simulationService.stopCommandSimulation();
        return ApiResponse.ok("命令下报模拟已停止", null);
    }

    /**
     * Stop all simulations
     */
    @PostMapping("/stop")
    public ApiResponse<Void> stopAll() {
        simulationService.stopAll();
        return ApiResponse.ok("所有模拟已停止", null);
    }

    /**
     * Get simulation status
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        return ApiResponse.ok(simulationService.getStatus());
    }

    /**
     * Manually trigger one round of data upload
     */
    @PostMapping("/upload/once")
    public ApiResponse<Map<String, Object>> triggerUploadOnce() {
        return ApiResponse.ok("已触发一次数据上发", simulationService.triggerOnce());
    }

    /**
     * Manually trigger one round of command delivery
     */
    @PostMapping("/delivery/once")
    public ApiResponse<Map<String, Object>> triggerDeliveryOnce() {
        simulationService.simulateCommandDelivery();
        return ApiResponse.ok("已触发一次命令下报", null);
    }

    /**
     * Get command log history
     */
    @GetMapping("/commands")
    public ApiResponse<List<CommandLog>> getCommandLogs(
            @RequestParam(required = false) String deviceId) {
        Long uid = securityUtils.getCurrentUserId();
        if (uid == null) return ApiResponse.ok(List.of());
        if (deviceId != null && !deviceId.isEmpty()) {
            return ApiResponse.ok(commandLogRepository.findByDeviceIdAndOwnerIdOrderBySentAtDesc(deviceId, uid));
        }
        return ApiResponse.ok(commandLogRepository.findTop20ByOwnerIdOrderBySentAtDesc(uid));
    }
}
