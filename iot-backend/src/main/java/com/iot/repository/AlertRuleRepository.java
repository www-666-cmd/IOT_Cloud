package com.iot.repository;

import com.iot.model.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabledTrue();

    List<AlertRule> findByDeviceIdAndEnabledTrue(String deviceId);

    List<AlertRule> findByProductTypeAndDeviceIdIsNullAndEnabledTrue(String productType);

    List<AlertRule> findByProductTypeAndEnabledTrue(String productType);

    long countByEnabledTrue();
}
