package com.iot.config;
// MQTT 配置类
import com.iot.service.MqttMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
@Slf4j
public class MqttConfig {

    @Value("${app.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${app.mqtt.client-id}")
    private String clientId;

    @Value("${app.mqtt.username:}")
    private String username;

    @Value("${app.mqtt.password:}")
    private String password;

    @Value("${app.mqtt.topics.telemetry:iot/+/telemetry}")
    private String telemetryTopic;

    @Value("${app.mqtt.topics.status:iot/+/status}")
    private String statusTopic;

    @Value("${app.mqtt.topics.command:iot/+/command}")
    private String commandTopic;

    private final MqttMessageHandler handler;

    public MqttConfig(MqttMessageHandler handler) {
        this.handler = handler;
    }

    @Bean
    public MqttClient mqttClient() {
        log.info("MQTT creating client, brokerUrl={}, clientId={}", brokerUrl, clientId);

        try {
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

            if (username != null && !username.isBlank()) {
                options.setUserName(username);
                options.setPassword(password != null ? password.toCharArray() : new char[0]);
            }

            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("MQTT connect complete, reconnect={}, serverURI={}", reconnect, serverURI);
                    subscribeTopics(client);
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                    handler.handleMessage(topic, payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            client.connect(options);
            subscribeTopics(client);
            log.info("MQTT connected to {}", brokerUrl);
            return client;
        } catch (MqttException e) {
            log.warn("MQTT unavailable (broker not running?), application will continue. "
                    + "brokerUrl={}, reasonCode={}", brokerUrl, e.getReasonCode());
            return null;
        } catch (Exception e) {
            log.warn("MQTT unavailable, application will continue. error={}", e.getMessage());
            return null;
        }
    }

    private void subscribeTopics(MqttClient client) {
        try {
            if (client.isConnected()) {
                client.subscribe(telemetryTopic, 1);
                client.subscribe(statusTopic, 1);
                client.subscribe(commandTopic, 1);
                log.info("MQTT subscribed topics: {}, {}, {}", telemetryTopic, statusTopic, commandTopic);
            } else {
                log.warn("MQTT subscribe skipped because client is not connected");
            }
        } catch (MqttException e) {
            log.warn("MQTT subscribe failed, reasonCode={}, message={}",
                    e.getReasonCode(), e.getMessage());
        }
    }
}