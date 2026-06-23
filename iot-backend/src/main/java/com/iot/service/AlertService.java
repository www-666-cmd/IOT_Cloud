package com.iot.service;

import com.iot.config.SecurityUtils;
import com.iot.model.*;
import com.iot.repository.AlertRecordRepository;
import com.iot.repository.AlertRuleRepository;
import com.iot.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AlertService {

    private final AlertRuleRepository ruleRepository;
    private final AlertRecordRepository recordRepository;
    private final DeviceRepository deviceRepository;
    private final SecurityUtils securityUtils;

    public AlertService(AlertRuleRepository ruleRepository,
                        AlertRecordRepository recordRepository,
                        DeviceRepository deviceRepository,
                        SecurityUtils securityUtils) {
        this.ruleRepository = ruleRepository;
        this.recordRepository = recordRepository;
        this.deviceRepository = deviceRepository;
        this.securityUtils = securityUtils;
    }

    private Long currentUserId() {
        Long id = securityUtils.getCurrentUserId();
        if (id == null) throw new RuntimeException("用户未登录");
        return id;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RedisCacheService redis;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WebSocketPushService wsPush;

    // ========== 规则管理 ==========

    public List<AlertRule> getAllRules() {
        return ruleRepository.findAll();
    }

    public AlertRule getRule(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("告警规则不存在: " + id));
    }

    @Transactional
    public AlertRule createRule(AlertRule rule) {
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    @Transactional
    public AlertRule updateRule(Long id, AlertRule updates) {
        AlertRule rule = getRule(id);
        rule.setName(updates.getName());
        rule.setConditionExpr(updates.getConditionExpr());
        rule.setLevel(updates.getLevel());
        rule.setDebounceSeconds(updates.getDebounceSeconds());
        rule.setEnabled(updates.isEnabled());
        rule.setNotifyEmail(updates.isNotifyEmail());
        rule.setNotifySms(updates.isNotifySms());
        rule.setNotifyPush(updates.isNotifyPush());
        rule.setDescription(updates.getDescription());
        rule.setSensorType(updates.getSensorType());
        rule.setDeviceId(updates.getDeviceId());
        rule.setProductType(updates.getProductType());
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);
    }

    @Transactional
    public AlertRule toggleRule(Long id, boolean enabled) {
        AlertRule rule = getRule(id);
        rule.setEnabled(enabled);
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    // ========== 告警记录查询 ==========

    public Page<AlertRecord> getAlertRecords(String deviceId, String status,
                                              String level, Pageable pageable) {
        return recordRepository.search(deviceId, status, level, currentUserId(), pageable);
    }

    /** 内部调用：按 ownerId 查询（供后台任务使用） */
    public Page<AlertRecord> getAlertRecordsByOwner(String deviceId, String status,
                                                     String level, Long ownerId, Pageable pageable) {
        return recordRepository.search(deviceId, status, level, ownerId, pageable);
    }

    public AlertRecord getAlertRecord(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("告警记录不存在: " + id));
    }

    @Transactional
    public AlertRecord acknowledgeAlert(Long id) {
        AlertRecord record = getAlertRecord(id);
        record.setStatus("ACKNOWLEDGED");
        record.setAcknowledgedAt(LocalDateTime.now());
        return recordRepository.save(record);
    }

    @Transactional
    public AlertRecord resolveAlert(Long id) {
        AlertRecord record = getAlertRecord(id);
        record.setStatus("RESOLVED");
        record.setResolvedAt(LocalDateTime.now());
        return recordRepository.save(record);
    }

    public Map<String, Long> getAlertStats() {
        Long uid = currentUserId();
        return Map.of(
                "total", recordRepository.countByOwnerId(uid),
                "active", recordRepository.countActiveAlerts(uid),
                "acknowledged", recordRepository.countByStatus("ACKNOWLEDGED", uid),
                "resolved", recordRepository.countByStatus("RESOLVED", uid)
        );
    }

    @Transactional
    public void deleteAlert(Long id) {
        recordRepository.deleteById(id);
    }

    // ========== 核心告警评估引擎 ==========

    /**
     * 对一条设备数据评估所有匹配的告警规则
     * 被 DataService 和 KafkaConsumerService 调用
     */
    @Transactional
    public void evaluate(String deviceId, String sensorType, double value, String deviceType) {
        Device device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) return;

        List<AlertRule> rules = findMatchingRules(deviceId, sensorType, deviceType);
        if (rules.isEmpty()) return;

        for (AlertRule rule : rules) {
            try {
                if (evaluateCondition(rule.getConditionExpr(), value)) {
                    // Redis 防抖检查（Redis 不可用时跳过防抖，直接触发告警）
                    boolean shouldTrigger = redis == null
                            || redis.checkAlertDebounce(device.getId(), rule.getId(), rule.getDebounceSeconds());
                    if (shouldTrigger) {

                        String title = buildAlertTitle(rule, device, sensorType, value);
                        String detail = buildAlertDetail(device, sensorType, value, rule);

                        AlertRecord record = AlertRecord.builder()
                                .deviceId(deviceId)
                                .deviceName(device.getName())
                                .ruleId(rule.getId())
                                .ruleName(rule.getName())
                                .level(rule.getLevel())
                                .title(title)
                                .detail(detail)
                                .ownerId(device.getOwnerId())
                                .build();

                        recordRepository.save(record);
                        log.info("ALERT triggered: {} [{}] device={} sensor={} value={}",
                                rule.getName(), rule.getLevel(), deviceId, sensorType, value);
                        if (wsPush != null) {
                            wsPush.pushAlert(deviceId, rule.getLevel(), title);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to evaluate rule {} for device {}", rule.getId(), deviceId, e);
            }
        }
    }

    private List<AlertRule> findMatchingRules(String deviceId, String sensorType, String deviceType) {
        // 精确匹配设备ID
        List<AlertRule> deviceRules = ruleRepository.findByDeviceIdAndEnabledTrue(deviceId);
        if (!deviceRules.isEmpty()) return deviceRules.stream()
                .filter(r -> r.getSensorType() == null || sensorType == null
                        || r.getSensorType().equals(sensorType))
                .toList();

        // 按产品类型匹配
        if (deviceType != null) {
            return ruleRepository.findByProductTypeAndDeviceIdIsNullAndEnabledTrue(deviceType).stream()
                    .filter(r -> r.getSensorType() == null || sensorType == null
                            || r.getSensorType().equals(sensorType))
                    .toList();
        }

        return List.of();
    }

    /**
     * 条件表达式求值
     * 支持: "value > 80", "value < 10", "value >= 90 AND value <= 100"
     * 使用 Java ScriptEngine (Nashorn 替代方案) 安全求值
     */
    private boolean evaluateCondition(String expr, double value) {
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
            if (engine == null) return simpleEvaluate(expr, value);
            engine.put("value", value);
            Object result = engine.eval(expr.replace("AND", "&&").replace("OR", "||"));
            return result instanceof Boolean ? (Boolean) result : false;
        } catch (Exception e) {
            // 降级到简单表达式求值
            return simpleEvaluate(expr, value);
        }
    }

    private boolean simpleEvaluate(String expr, double value) {
        expr = expr.replace("value", String.valueOf(value)).trim();
        try {
            if (expr.contains(">=")) {
                return value >= Double.parseDouble(expr.substring(expr.indexOf(">=") + 2).trim());
            } else if (expr.contains("<=")) {
                return value <= Double.parseDouble(expr.substring(expr.indexOf("<=") + 2).trim());
            } else if (expr.contains(">")) {
                return value > Double.parseDouble(expr.substring(expr.indexOf(">") + 1).trim());
            } else if (expr.contains("<")) {
                return value < Double.parseDouble(expr.substring(expr.indexOf("<") + 1).trim());
            } else if (expr.contains("==")) {
                return Math.abs(value - Double.parseDouble(expr.substring(expr.indexOf("==") + 2).trim())) < 0.001;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    private String buildAlertTitle(AlertRule rule, Device device, String sensorType, double value) {
        return String.format("[%s] %s - %s: %.2f (阈值: %s)",
                rule.getLevel(), device.getName(), sensorType, value, rule.getConditionExpr());
    }

    private String buildAlertDetail(Device device, String sensorType, double value, AlertRule rule) {
        return String.format(
                "{\"deviceId\":\"%s\",\"deviceName\":\"%s\",\"sensorType\":\"%s\",\"value\":%.2f,\"condition\":\"%s\",\"level\":\"%s\",\"ruleId\":%d}",
                device.getDeviceId(), device.getName(), sensorType, value,
                rule.getConditionExpr(), rule.getLevel(), rule.getId());
    }
}
