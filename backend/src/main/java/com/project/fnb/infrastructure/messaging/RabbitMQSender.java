package com.project.fnb.infrastructure.messaging;

import com.project.fnb.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitMQSender {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Gửi sự kiện async
     * @param routingKey Định tuyến (Ví dụ: tenant.tenant_A.kitchen)
     * @param payload Dữ liệu (Object DTO)
     */
    public void send(String routingKey, Object payload) {
        try {
            log.info("Sending message to [{}]: {}", routingKey, payload);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, payload);
        } catch (Exception e) {
            log.error("Failed to send RabbitMQ message", e);
        }
    }
}