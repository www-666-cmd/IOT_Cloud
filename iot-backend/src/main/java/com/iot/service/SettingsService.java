package com.iot.service;

import com.iot.model.SystemSettings;
import com.iot.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingsRepository settingsRepository;

    public SystemSettings getSettings(Long userId) {
        return settingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefault(userId));
    }

    @Transactional
    public SystemSettings updateSettings(Long userId, Map<String, Object> updates) {
        SystemSettings settings = getSettings(userId);

        // 告警通知设置
        if (updates.containsKey("notifications")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> notif = (Map<String, Object>) updates.get("notifications");
            if (notif.containsKey("email")) settings.setNotifyEmail((Boolean) notif.get("email"));
            if (notif.containsKey("sms")) settings.setNotifySms((Boolean) notif.get("sms"));
            if (notif.containsKey("push")) settings.setNotifyPush((Boolean) notif.get("push"));
            if (notif.containsKey("alertThreshold")) settings.setAlertThreshold((Integer) notif.get("alertThreshold"));
        }

        // 数据设置
        if (updates.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) updates.get("data");
            if (data.containsKey("autoRefresh")) settings.setAutoRefresh((Boolean) data.get("autoRefresh"));
            if (data.containsKey("refreshInterval")) settings.setRefreshInterval((Integer) data.get("refreshInterval"));
            if (data.containsKey("dataRetention")) settings.setDataRetention((Integer) data.get("dataRetention"));
        }

        // 系统设置
        if (updates.containsKey("system")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sys = (Map<String, Object>) updates.get("system");
            if (sys.containsKey("theme")) settings.setTheme((String) sys.get("theme"));
            if (sys.containsKey("language")) settings.setLanguage((String) sys.get("language"));
        }

        settings.setUpdatedAt(LocalDateTime.now());
        return settingsRepository.save(settings);
    }

    private SystemSettings createDefault(Long userId) {
        return settingsRepository.save(SystemSettings.builder().userId(userId).build());
    }
}
