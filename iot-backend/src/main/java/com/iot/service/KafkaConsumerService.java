package com.iot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Kafka 消费者 - 消费设备数据并进行多路处理：
 * 1. 更新 Redis 设备状态
 * 2. 触发告警规则评估
 * 3. 写入时序数据库 (TDengine/JPA)
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Slf4j
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private RedisCacheService redisCacheService;

    public KafkaConsumerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "${app.kafka.topics.device-telemetry}", containerFactory = "kafkaListenerContainerFactory")
    public void onDeviceTelemetry(String message, Acknowledgment ack) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String deviceId = (String) data.get("deviceId");

            // 更新 Redis 设备影子
            if (redisCacheService != null) {
                redisCacheService.setDeviceShadow(deviceId, data);
                redisCacheService.setDeviceOnline(deviceId, true);
            }

            log.debug("Processed telemetry: device={}", deviceId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process telemetry message", e);
        }
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "${app.kafka.topics.device-status}", containerFactory = "kafkaListenerContainerFactory")
    public void onDeviceStatus(String message, Acknowledgment ack) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            String deviceId = (String) data.get("deviceId");
            String status = (String) data.get("status");

            boolean online = "ONLINE".equalsIgnoreCase(status);
            if (redisCacheService != null) {
                redisCacheService.setDeviceOnline(deviceId, online);
            }

            log.debug("Device status changed: {} -> {}", deviceId, status);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process status message", e);
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.alert-events}", containerFactory = "kafkaListenerContainerFactory")
    public void onAlertEvent(String message, Acknowledgment ack) {
        try {
            log.info("Alert event received: {}", message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process alert event", e);
        }
    }
}
