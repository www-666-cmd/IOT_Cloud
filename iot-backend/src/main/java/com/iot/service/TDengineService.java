package com.iot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@ConditionalOnProperty(name = "app.tdengine.enabled", havingValue = "true")
@Slf4j
public class TDengineService {

    private final JdbcTemplate tdengineJdbc;
    private final int batchSize;
    private final long batchIntervalMs;

    // 批量写入缓冲区
    private final List<Object[]> writeBuffer = new CopyOnWriteArrayList<>();
    private long lastFlushTime = System.currentTimeMillis();

    public TDengineService(@Qualifier("tdengineJdbcTemplate") JdbcTemplate tdengineJdbc,
                           @Value("${app.tdengine.batch-size:1000}") int batchSize,
                           @Value("${app.tdengine.batch-interval-ms:5000}") long batchIntervalMs) {
        this.tdengineJdbc = tdengineJdbc;
        this.batchSize = batchSize;
        this.batchIntervalMs = batchIntervalMs;
    }

    @PostConstruct
    public void init() {
        createDatabase();
        createSuperTable();
        log.info("TDengine: database and super table initialized");
    }

    // ========== 初始化 ==========

    private void createDatabase() {
        try {
            tdengineJdbc.execute("CREATE DATABASE IF NOT EXISTS iot_telemetry " +
                    "KEEP 365 DAYS 10 BLOCKS 4 " +
                    "UPDATE 1 " +
                    "CACHEMODEL 'LAST_ROW'");
        } catch (Exception e) {
            log.warn("TDengine create database failed (may already exist): {}", e.getMessage());
        }
    }

    private void createSuperTable() {
        try {
            tdengineJdbc.execute(
                    "CREATE STABLE IF NOT EXISTS iot_telemetry.device_telemetry (" +
                    "  ts          TIMESTAMP, " +
                    "  device_id   NCHAR(64), " +
                    "  sensor_id   NCHAR(64), " +
                    "  sensor_type NCHAR(50), " +
                    "  val       DOUBLE, " +
                    "  unit        NCHAR(20) " +
                    ") TAGS (" +
                    "  device_id_tag   NCHAR(64), " +
                    "  product_type    NCHAR(50) " +
                    ")");
        } catch (Exception e) {
            log.warn("TDengine create super table failed: {}", e.getMessage());
        }
    }

    // ========== 数据写入 ==========

    /**
     * 写入一条时序数据（攒批）
     */
    public void insert(String deviceId, String sensorId, String sensorType,
                       double value, String unit, LocalDateTime ts) {
        Object[] row = {
                Timestamp.valueOf(ts),
                deviceId,
                sensorId,
                sensorType,
                value,
                unit
        };
        writeBuffer.add(row);

        if (writeBuffer.size() >= batchSize || shouldFlush()) {
            flushBuffer();
        }
    }

    private boolean shouldFlush() {
        return System.currentTimeMillis() - lastFlushTime > batchIntervalMs;
    }

    /**
     * 批量写入 TDengine，自动创建子表
     */
    public synchronized void flushBuffer() {
        if (writeBuffer.isEmpty()) return;

        List<Object[]> batch = new ArrayList<>(writeBuffer);
        writeBuffer.clear();
        lastFlushTime = System.currentTimeMillis();

        // 按 deviceId 分组，批量写子表
        Map<String, List<Object[]>> grouped = new LinkedHashMap<>();
        for (Object[] row : batch) {
            String deviceId = (String) row[1];
            grouped.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(row);
        }

        int totalRows = 0;
        for (Map.Entry<String, List<Object[]>> entry : grouped.entrySet()) {
            String deviceId = entry.getKey();
            String subTable = "d_" + deviceId.replace("-", "_").replace(".", "_");

            // 自动创建子表
            ensureSubTable(subTable, deviceId);

            // 批量 INSERT
            StringBuilder sql = new StringBuilder("INSERT INTO iot_telemetry." + subTable +
                    " (ts, device_id, sensor_id, sensor_type, val, unit) VALUES ");

            List<Object> params = new ArrayList<>();
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("(?, ?, ?, ?, ?, ?)");
                Object[] row = entry.getValue().get(i);
                params.add(row[0]); // ts
                params.add(row[1]); // device_id
                params.add(row[2]); // sensor_id
                params.add(row[3]); // sensor_type
                params.add(row[4]); // value
                params.add(row[5]); // unit
            }

            try {
                tdengineJdbc.update(sql.toString(), params.toArray());
                totalRows += entry.getValue().size();
            } catch (Exception e) {
                log.error("TDengine batch insert failed for device {}", deviceId, e);
            }
        }
        log.info("TDengine: flushed {} rows to {} sub-tables", totalRows, grouped.size());
    }

    private void ensureSubTable(String subTable, String deviceId) {
        try {
            tdengineJdbc.execute(
                    "CREATE TABLE IF NOT EXISTS iot_telemetry." + subTable +
                    " USING iot_telemetry.device_telemetry " +
                    " TAGS('" + deviceId + "', 'default')");
        } catch (Exception e) {
            log.debug("TDengine sub-table {} already exists", subTable);
        }
    }

    // ========== 数据查询 ==========

    /**
     * 查询单设备最新 N 条数据
     */
    public List<Map<String, Object>> queryLatest(String deviceId, String sensorId, int limit) {
        String subTable = "d_" + deviceId.replace("-", "_").replace(".", "_");
        StringBuilder sql = new StringBuilder(
                "SELECT ts, device_id, sensor_id, sensor_type, val , unit " +
                "FROM iot_telemetry." + subTable);
        if (sensorId != null && !sensorId.isEmpty()) {
            sql.append(" WHERE sensor_id = '").append(sensorId).append("'");
        }
        sql.append(" ORDER BY ts DESC LIMIT ").append(limit);

        try {
            return tdengineJdbc.queryForList(sql.toString());
        } catch (Exception e) {
            log.warn("TDengine query failed for device {}", deviceId, e);
            return List.of();
        }
    }

    /**
     * 查询单设备时间范围数据，带降采样聚合
     */
    public List<Map<String, Object>> queryRange(String deviceId, String sensorId,
                                                 LocalDateTime from, LocalDateTime to,
                                                 String interval) {
        String subTable = "d_" + deviceId.replace("-", "_").replace(".", "_");
        StringBuilder sql = new StringBuilder(
                "SELECT ts, sensor_id, AVG(val) as avg_val, MAX(val) as max_val, MIN(val) as min_val " +
                "FROM iot_telemetry." + subTable +
                " WHERE ts >= '" + Timestamp.valueOf(from) + "' AND ts <= '" + Timestamp.valueOf(to) + "'");

        if (sensorId != null && !sensorId.isEmpty()) {
            sql.append(" AND sensor_id = '").append(sensorId).append("'");
        }

        if (interval != null && !interval.isEmpty()) {
            sql.append(" INTERVAL(").append(interval).append(") FILL(linear)");
        }

        sql.append(" GROUP BY sensor_id");

        try {
            return tdengineJdbc.queryForList(sql.toString());
        } catch (Exception e) {
            log.warn("TDengine range query failed for device {}", deviceId, e);
            return List.of();
        }
    }

    /**
     * 按产品类型聚合查询（跨设备超表查询）
     */
    public List<Map<String, Object>> queryByProductType(String productType, String sensorType,
                                                         LocalDateTime from, LocalDateTime to,
                                                         String interval) {
        StringBuilder sql = new StringBuilder(
                "SELECT ts, AVG(val) as avg_val, MAX(val) as max_val, MIN(val) as min_val " +
                "FROM iot_telemetry.device_telemetry " +
                "WHERE product_type = '").append(productType).append("'")
                .append(" AND ts >= '").append(Timestamp.valueOf(from)).append("'")
                .append(" AND ts <= '").append(Timestamp.valueOf(to)).append("'");

        if (sensorType != null && !sensorType.isEmpty()) {
            sql.append(" AND sensor_type = '").append(sensorType).append("'");
        }

        if (interval != null && !interval.isEmpty()) {
            sql.append(" INTERVAL(").append(interval).append(")");
        }

        try {
            return tdengineJdbc.queryForList(sql.toString());
        } catch (Exception e) {
            log.warn("TDengine aggregate query failed for product {}", productType, e);
            return List.of();
        }
    }

    /**
     * 数据清理（按保留天数）
     */
    public void cleanOldData(int retentionDays) {
        try {
            String sql = "DELETE FROM iot_telemetry.device_telemetry WHERE ts < NOW - " + retentionDays + "d";
            int deleted = tdengineJdbc.update(sql);
            log.info("TDengine: deleted {} rows older than {} days", deleted, retentionDays);
        } catch (Exception e) {
            log.warn("TDengine clean failed", e);
        }
    }

    /**
     * 应用关闭时刷新缓冲区
     */
    public void flushOnShutdown() {
        flushBuffer();
    }
}
