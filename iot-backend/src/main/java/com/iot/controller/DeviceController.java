package com.iot.controller;

import com.iot.dto.ApiResponse;
import com.iot.dto.DeviceRequest;
import com.iot.model.Device;
import com.iot.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ApiResponse<List<Device>> getAllDevices() {
        return ApiResponse.ok(deviceService.getAllDevices());
    }

    @GetMapping("/{deviceId}")
    public ApiResponse<Device> getDevice(@PathVariable String deviceId) {
        return ApiResponse.ok(deviceService.getDeviceById(deviceId));
    }

    @PostMapping
    public ApiResponse<Device> createDevice(@Valid @RequestBody DeviceRequest request) {
        return ApiResponse.ok("设备添加成功", deviceService.createDevice(request));
    }

    @PutMapping("/{deviceId}")
    public ApiResponse<Device> updateDevice(@PathVariable String deviceId,
                                            @Valid @RequestBody DeviceRequest request) {
        return ApiResponse.ok("设备更新成功", deviceService.updateDevice(deviceId, request));
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> deleteDevice(@PathVariable String deviceId) {
        deviceService.deleteDevice(deviceId);
        return ApiResponse.ok("设备已删除", null);
    }

    @PatchMapping("/{deviceId}/status")
    public ApiResponse<Device> updateStatus(@PathVariable String deviceId,
                                            @RequestBody Map<String, String> body) {
        return ApiResponse.ok(deviceService.updateDeviceStatus(deviceId, body.get("status")));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> getStats() {
        Map<String, Long> stats = Map.of(
            "total", deviceService.countAll(),
            "online", deviceService.countByStatus("ONLINE"),
            "offline", deviceService.countByStatus("OFFLINE"),
            "warning", deviceService.countByStatus("WARNING")
        );
        return ApiResponse.ok(stats);
    }
}
