package com.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.model.Device;
import com.iot.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * MQTT 消息处理器：解析设备上报的遥测和状态数据，
 * 转发到 DataService 统一管线（TDengine → Kafka → Redis → 告警）
 */
@Service
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
@Slf4j
public class MqttMessageHandler {

    private final ObjectMapper objectMapper;
    private final DataService dataService;
    private final DeviceRepository deviceRepository;

    public MqttMessageHandler(ObjectMapper objectMapper, DataService dataService,
                              DeviceRepository deviceRepository) {
        this.objectMapper = objectMapper;
        this.dataService = dataService;
        this.deviceRepository = deviceRepository;
    }

    /**
     * 处理 MQTT 消息，根据 topic 类型分发
     * @param topic   MQTT topic (如 iot/dev_xxx/telemetry)
     * @param payload JSON 字符串
     */
    @SuppressWarnings("unchecked")
    public void handleMessage(String topic, String payload) {
        try {
            String[] parts = topic.split("/");
            if (parts.length < 3) return;
            String deviceId = parts[1];  // iot/{deviceId}/telemetry
            String type = parts[2];       // telemetry | status

            Map<String, Object> data = objectMapper.readValue(payload, Map.class);

            switch (type) {
                case "telemetry" -> {
                    String sensorId = (String) data.get("sensorId");
                    Double value = data.get("value") instanceof Number n ? n.doubleValue() : null;
                    if (sensorId != null && value != null) {
                        Long ownerId = deviceRepository.findByDeviceId(deviceId)
                                .map(Device::getOwnerId).orElse(null);
                        dataService.writeDataPoint(deviceId, sensorId, null, value, null, ownerId);
                        log.debug("MQTT telemetry: {} sensor={} value={}", deviceId, sensorId, value);
                    }
                }
                case "status" -> {
                    String status = (String) data.get("status");
                    if (status != null) {
                        deviceRepository.findByDeviceId(deviceId).ifPresent(d -> {
                            d.setStatus(status.toUpperCase());
                            deviceRepository.save(d);
                        });
                        log.info("MQTT status: {} → {}", deviceId, status);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("MQTT message parse failed: topic={}, error={}", topic, e.getMessage());
        }
    }
}
