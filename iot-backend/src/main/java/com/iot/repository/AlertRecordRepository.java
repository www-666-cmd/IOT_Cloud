package com.iot.repository;

import com.iot.model.AlertRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {

    Page<AlertRecord> findByDeviceIdOrderByTriggeredAtDesc(String deviceId, Pageable pageable);

    Page<AlertRecord> findByStatusOrderByTriggeredAtDesc(String status, Pageable pageable);

    Page<AlertRecord> findByLevelOrderByTriggeredAtDesc(String level, Pageable pageable);

    @Query("SELECT a FROM AlertRecord a WHERE "
         + "(:deviceId IS NULL OR a.deviceId = :deviceId) "
         + "AND (:status IS NULL OR a.status = :status) "
         + "AND (:level IS NULL OR a.level = :level) "
         + "AND (:ownerId IS NULL OR a.ownerId = :ownerId) "
         + "ORDER BY a.triggeredAt DESC")
    Page<AlertRecord> search(@Param("deviceId") String deviceId,
                             @Param("status") String status,
                             @Param("level") String level,
                             @Param("ownerId") Long ownerId,
                             Pageable pageable);

    @Query("SELECT COUNT(a) FROM AlertRecord a WHERE a.status = 'TRIGGERED' AND a.ownerId = :ownerId")
    long countActiveAlerts(@Param("ownerId") Long ownerId);

    @Query("SELECT COUNT(a) FROM AlertRecord a WHERE a.status = :status AND a.ownerId = :ownerId")
    long countByStatus(@Param("status") String status, @Param("ownerId") Long ownerId);

    @Query("SELECT COUNT(a) FROM AlertRecord a WHERE a.ownerId = :ownerId")
    long countByOwnerId(@Param("ownerId") Long ownerId);

    List<AlertRecord> findTop20ByOrderByTriggeredAtDesc();
}
