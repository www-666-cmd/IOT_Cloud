package com.iot.service;

import com.iot.config.SecurityUtils;
import com.iot.model.DataPoint;
import com.iot.model.Device;
import com.iot.model.Sensor;
import com.iot.repository.DataPointRepository;
import com.iot.repository.DeviceRepository;
import com.iot.repository.SensorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class DataService {

    private final DataPointRepository dataPointRepository;
    private final AlertService alertService;
    private final SecurityUtils securityUtils;

    // 防 Kafka 消费 → 再生产的循环
    private static final ThreadLocal<Boolean> FROM_KAFKA = ThreadLocal.withInitial(() -> false);
    private final DeviceRepository deviceRepository;
    private final SensorRepository sensorRepository;

    public DataService(DataPointRepository dataPointRepository,
                       AlertService alertService,
                       SecurityUtils securityUtils,
                       DeviceRepository deviceRepository,
                       SensorRepository sensorRepository) {
        this.dataPointRepository = dataPointRepository;
        this.alertService = alertService;
        this.securityUtils = securityUtils;
        this.deviceRepository = deviceRepository;
        this.sensorRepository = sensorRepository;
    }

    // 以下为可选组件，开发环境可能不可用
    @Autowired(required = false)
    private RedisCacheService redis;

    @Autowired(required = false)
    private TDengineService tdengine;

    @Autowired(required = false)
    private KafkaProducerService kafkaProducer;

    @Autowired(required = false)
    private WebSocketPushService wsPush;

    // PostgreSQL 批量写入缓冲区
    private final List<DataPoint> writeBuffer = new ArrayList<>(500);
    private long lastFlushTime = System.currentTimeMillis();
    private static final int BATCH_SIZE = 500;
    private static final long FLUSH_INTERVAL_MS = 5000;

    private Long currentUserId() {
        Long id = securityUtils.getCurrentUserId();
        if (id == null) throw new RuntimeException("用户未登录");
        return id;
    }

    // ========== 数据查询 ==========

    /**
     * 查询设备最新 N 条数据
     */
    public List<DataPoint> getDeviceData(String deviceId, String sensorId, int limit) {
        return getDeviceData(deviceId, sensorId, null, null, limit);
    }

    /**
     * 查询设备数据，支持时间范围过滤
     * 查询顺序: Redis 缓存 → TDengine → PostgreSQL
     */
    public List<DataPoint> getDeviceData(String deviceId, String sensorId,
                                          LocalDateTime from, LocalDateTime to, int limit) {
        // 1. 无时间范围时先查 Redis 缓存（仅缓存最新数据）
        if (from == null && to == null) {
            List<DataPoint> cached = getCachedData(deviceId, sensorId, limit);
            if (cached != null) return cached;
        }

        // 2. TDengine 查询（时间范围 + 聚合）
        if (tdengine != null) {
            try {
                List<Map<String, Object>> tdData;
                if (from != null && to != null) {
                    tdData = tdengine.queryRange(deviceId, sensorId, from, to, null);
                } else {
                    tdData = tdengine.queryLatest(deviceId, sensorId, limit);
                }
                if (!tdData.isEmpty()) {
                    List<DataPoint> result = tdData.stream()
                            .map(this::mapTdRowToDataPoint).toList();
                    safeSetCache(deviceId, sensorId, result);
                    return result;
                }
            } catch (Exception e) {
                log.warn("TDengine query failed, falling back to PostgreSQL: {}", e.getMessage());
            }
        }

        // 3. PostgreSQL fallback（按用户过滤）
        Long uid = currentUserId();
        List<DataPoint> data;
        if (sensorId != null && !sensorId.isEmpty()) {
            data = dataPointRepository.findByDeviceIdAndSensorIdAndOwnerIdOrderByTimestampDesc(deviceId, sensorId, uid);
        } else {
            data = dataPointRepository.findByDeviceIdAndOwnerIdOrderByTimestampDesc(deviceId, uid);
        }

        // 时间范围过滤
        if (from != null) {
            data = data.stream().filter(dp -> !dp.getTimestamp().isBefore(from)).toList();
        }
        if (to != null) {
            data = data.stream().filter(dp -> !dp.getTimestamp().isAfter(to)).toList();
        }

        List<DataPoint> result = data.size() > limit ? data.subList(0, limit) : data;
        if (from == null && to == null) {
            safeSetCache(deviceId, sensorId, result);
        }
        return result;
    }

    /**
     * 时间范围 + 降采样查询（仅 TDengine）
     */
    public List<Map<String, Object>> queryHistory(String deviceId, String sensorId,
                                                   LocalDateTime from, LocalDateTime to,
                                                   String interval) {
        if (tdengine != null) {
            try {
                return tdengine.queryRange(deviceId, sensorId, from, to, interval);
            } catch (Exception e) {
                log.warn("TDengine history query failed: {}", e.getMessage());
            }
        }
        // PostgreSQL fallback（按用户过滤）
        List<DataPoint> data = dataPointRepository.findByDeviceIdAndSensorIdAndOwnerIdOrderByTimestampDesc(
                deviceId, sensorId, currentUserId());
        return data.stream()
                .filter(dp -> !dp.getTimestamp().isBefore(from) && !dp.getTimestamp().isAfter(to))
                .map(dp -> Map.<String, Object>of(
                        "ts", dp.getTimestamp().toString(),
                        "value", dp.getValue(),
                        "sensor_id", dp.getSensorId()))
                .toList();
    }

    public List<Map<String, Object>> queryByProduct(String productType, String sensorType,
                                                     LocalDateTime from, LocalDateTime to,
                                                     String interval) {
        if (tdengine != null) {
            return tdengine.queryByProductType(productType, sensorType, from, to, interval);
        }
        return List.of();
    }

    // ========== 数据写入 ==========

    @Transactional
    public synchronized DataPoint addDataPoint(String deviceId, String sensorId, Double value) {
        return addDataPoint(deviceId, sensorId, null, value, null);
    }

    @Transactional
    public synchronized DataPoint addDataPoint(String deviceId, String sensorId,
                                                String sensorType, Double value, String unit) {
        Long uid = currentUserId();
        // 传感器类型未提供时自动查补
        if (sensorType == null) {
            sensorType = sensorRepository.findById(sensorId).map(Sensor::getType).orElse(null);
        }
        if (unit == null) {
            unit = sensorRepository.findById(sensorId).map(Sensor::getUnit).orElse(null);
        }
        return writeDataPoint(deviceId, sensorId, sensorType, value, unit, uid);
    }

    /** 供 Kafka Consumer 调用：标记来自 Kafka，避免循环重发 */
    public DataPoint fromKafka(String deviceId, String sensorId, String sensorType,
                                Double value, String unit, Long ownerId) {
        FROM_KAFKA.set(true);
        try {
            return writeDataPoint(deviceId, sensorId, sensorType, value, unit, ownerId);
        } finally {
            FROM_KAFKA.remove();
        }
    }

    /** 内部写入方法：允许直接指定 ownerId，供后台任务/仿真使用 */
    DataPoint writeDataPoint(String deviceId, String sensorId, String sensorType,
                             Double value, String unit, Long ownerId) {
        LocalDateTime now = LocalDateTime.now();
        DataPoint dp = DataPoint.builder()
                .deviceId(deviceId)
                .sensorId(sensorId)
                .value(value)
                .ownerId(ownerId)
                .timestamp(now)
                .build();

        if (tdengine != null) {
            try {
                tdengine.insert(deviceId, sensorId,
                        sensorType != null ? sensorType : "unknown",
                        value, unit != null ? unit : "", now);
            } catch (Exception e) {
                log.warn("TDengine write failed, fallback to PostgreSQL: {}", e.getMessage());
                writeBuffer.add(dp);
                flushPgBuffer();  // TDengine 失败时立即落盘 PostgreSQL
            }
        } else {
            writeBuffer.add(dp);
            if (writeBuffer.size() >= BATCH_SIZE || shouldFlush()) {
                flushPgBuffer();
            }
        }

        // 更新 Sensor 实体的实时值（前端显示用）
        updateSensorValue(deviceId, sensorId, value);

        // WebSocket 实时推送
        if (wsPush != null) {
            wsPush.pushDeviceData(deviceId, sensorId, value, unit);
        }

        sendKafkaTelemetry(deviceId, sensorId, sensorType, value, unit);
        safeUpdateShadow(deviceId, sensorId, value);
        safeDeleteCache(deviceId, sensorId);

        try {
            alertService.evaluate(deviceId, sensorType != null ? sensorType : sensorId,
                    value, null);
        } catch (Exception e) {
            log.error("Alert eval failed: device={} sensor={}", deviceId, sensorId, e);
        }

        return dp;
    }

    private void updateSensorValue(String deviceId, String sensorId, double value) {
        try {
            sensorRepository.findById(sensorId).ifPresent(sensor -> {
                sensor.setValue(value);
                sensorRepository.save(sensor);
            });
            deviceRepository.findByDeviceId(deviceId).ifPresent(device -> {
                device.setLastActive(LocalDateTime.now());
                device.setStatus("ONLINE");
                deviceRepository.save(device);
            });
        } catch (Exception e) {
            log.debug("Update sensor value failed (non-critical): {}", e.getMessage());
        }
    }

    private void sendKafkaTelemetry(String deviceId, String sensorId, String sensorType,
                                     Double value, String unit) {
        if (kafkaProducer == null || Boolean.TRUE.equals(FROM_KAFKA.get())) return;
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("deviceId", deviceId);
            msg.put("sensorId", sensorId);
            msg.put("sensorType", sensorType != null ? sensorType : "unknown");
            msg.put("value", value);
            msg.put("unit", unit != null ? unit : "");
            msg.put("timestamp", System.currentTimeMillis());
            kafkaProducer.sendDeviceTelemetry(deviceId, msg);
        } catch (Exception e) {
            log.debug("Kafka telemetry send failed (non-critical): {}", e.getMessage());
        }
    }

    // ========== 命令下发 ==========

    @Transactional
    public String sendCommand(String deviceId, String command, Object params) {
        // 如果是控制类命令，同步更新执行器状态
        String actuatorName = extractActuatorName(params);
        String result = applyActuatorCommand(deviceId, actuatorName, command);

        if (redis != null) {
            safeRedis(() -> redis.setCache("cmd:" + UUID.randomUUID(),
                    Map.of("deviceId", deviceId, "command", command, "params", params, "status", "SENT"), 300));
        }
        return result;
    }

    private String extractActuatorName(Object params) {
        if (params instanceof Map) {
            Object actuator = ((Map<?, ?>) params).get("actuator");
            if (actuator != null) return actuator.toString();
        }
        return null;
    }

    private String applyActuatorCommand(String deviceId, String actuatorName, String command) {
        if (actuatorName == null) {
            return "命令 [" + command + "] 已发送至设备 " + deviceId;
        }
        // 查找匹配的执行器
        try {
            var deviceOpt = deviceRepository.findByDeviceId(deviceId);
            if (deviceOpt.isEmpty()) {
                return "命令 [" + command + "] 已发送至设备 " + deviceId + "（设备不存在）";
            }
            Device device = deviceOpt.get();
            for (Sensor sensor : device.getSensors()) {
                if (actuatorName.equals(sensor.getName())) {
                    double newValue;
                    switch (command.toLowerCase()) {
                        case "on":
                        case "1":
                            newValue = 1.0;
                            break;
                        case "off":
                        case "0":
                            newValue = 0.0;
                            break;
                        case "toggle":
                            newValue = sensor.getValue() > 0.5 ? 0.0 : 1.0;
                            break;
                        default:
                            return "命令 [" + command + "] 已发送至设备 " + deviceId;
                    }
                    sensor.setValue(newValue);
                    sensorRepository.save(sensor);
                    device.setLastActive(LocalDateTime.now());
                    device.setStatus("ONLINE");
                    deviceRepository.save(device);

                    // 写入 TDengine 时序记录，供历史数据查询
                    writeDataPoint(deviceId, sensor.getId(), sensor.getType(),
                            newValue, sensor.getUnit(), device.getOwnerId());

                    String state = newValue > 0.5 ? "ON" : "OFF";
                    return "命令 [" + command + "] → " + actuatorName + " 已" + state;
                }
            }
        } catch (Exception e) {
            log.warn("Actuator command apply failed: {}", e.getMessage());
        }
        return "命令 [" + command + "] 已发送至设备 " + deviceId;
    }

    // ========== 数据清理 ==========

    public void cleanOldData(int retentionDays) {
        if (tdengine != null) {
            try {
                tdengine.cleanOldData(retentionDays);
            } catch (Exception e) {
                log.warn("TDengine clean failed: {}", e.getMessage());
            }
        }
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        dataPointRepository.deleteByTimestampBeforeAndOwnerId(before, currentUserId());
    }

    // ========== 生命周期 ==========

    public void flushOnShutdown() {
        if (tdengine != null) {
            try {
                tdengine.flushOnShutdown();
            } catch (Exception e) {
                log.warn("TDengine shutdown flush failed: {}", e.getMessage());
            }
        }
        flushPgBuffer();
    }

    // ========== Redis 安全调用 ==========

    private void safeRedis(Runnable action) {
        if (redis == null) return;
        try {
            action.run();
        } catch (Exception e) {
            log.debug("Redis operation failed (non-critical): {}", e.getMessage());
        }
    }

    private List<DataPoint> getCachedData(String deviceId, String sensorId, int limit) {
        if (redis == null) return null;
        try {
            String key = "data:recent:" + deviceId + (sensorId != null ? ":" + sensorId : "");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cached = redis.getCache(key, List.class);
            if (cached != null && !cached.isEmpty() && cached.size() >= limit) {
                return cached.stream().map(this::mapToDataPoint).limit(limit).toList();
            }
        } catch (Exception e) {
            log.debug("Redis cache miss: {}", e.getMessage());
        }
        return null;
    }

    private void safeSetCache(String deviceId, String sensorId, List<DataPoint> data) {
        safeRedis(() -> {
            String key = "data:recent:" + deviceId + (sensorId != null ? ":" + sensorId : "");
            redis.setCache(key, data, 30);
        });
    }

    private void safeUpdateShadow(String deviceId, String sensorId, Double value) {
        safeRedis(() -> {
            Map<String, Object> shadow = new HashMap<>();
            shadow.put("deviceId", deviceId);
            shadow.put("sensorId", sensorId);
            shadow.put("value", value);
            shadow.put("updatedAt", System.currentTimeMillis());
            redis.setDeviceShadow(deviceId, shadow);
            redis.setDeviceOnline(deviceId, true);
        });
    }

    private void safeDeleteCache(String deviceId, String sensorId) {
        safeRedis(() -> {
            redis.deleteCache("data:recent:" + deviceId);
            redis.deleteCache("data:recent:" + deviceId + ":" + sensorId);
        });
    }

    // ========== 缓冲区 ==========

    private boolean shouldFlush() {
        return System.currentTimeMillis() - lastFlushTime > FLUSH_INTERVAL_MS;
    }

    private void flushPgBuffer() {
        if (writeBuffer.isEmpty()) return;
        List<DataPoint> batch = new ArrayList<>(writeBuffer);
        writeBuffer.clear();
        lastFlushTime = System.currentTimeMillis();
        dataPointRepository.saveAll(batch);
        log.debug("Flushed {} data points to PostgreSQL", batch.size());
    }

    // ========== 映射工具 ==========

    private DataPoint mapToDataPoint(Map<String, Object> map) {
        DataPoint dp = new DataPoint();
        dp.setId(map.get("id") != null ? ((Number) map.get("id")).longValue() : null);
        dp.setDeviceId((String) map.get("deviceId"));
        dp.setSensorId((String) map.get("sensorId"));
        dp.setValue(map.get("value") != null ? ((Number) map.get("value")).doubleValue() : 0.0);
        if (map.get("timestamp") instanceof String) {
            dp.setTimestamp(LocalDateTime.parse((String) map.get("timestamp")));
        }
        return dp;
    }

    private DataPoint mapTdRowToDataPoint(Map<String, Object> row) {
        DataPoint dp = new DataPoint();
        dp.setDeviceId((String) row.get("device_id"));
        dp.setSensorId((String) row.get("sensor_id"));
        dp.setValue(row.get("val") != null ? ((Number) row.get("val")).doubleValue() : 0.0);
        Object ts = row.get("ts");
        if (ts instanceof Timestamp) {
            dp.setTimestamp(((Timestamp) ts).toLocalDateTime());
        }
        return dp;
    }
}
