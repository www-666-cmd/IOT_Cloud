package com.iot.repository;

import com.iot.model.CommandLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommandLogRepository extends JpaRepository<CommandLog, Long> {

    List<CommandLog> findByDeviceIdAndOwnerIdOrderBySentAtDesc(String deviceId, Long ownerId);

    List<CommandLog> findTop20ByOwnerIdOrderBySentAtDesc(Long ownerId);
}
