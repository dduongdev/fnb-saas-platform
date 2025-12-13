package com.project.fnb.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "fnb.core.exchange";
    public static final String QUEUE_KITCHEN = "q.kitchen.notification"; 
    public static final String ROUTING_KEY_KITCHEN = "tenant.*.kitchen"; 

    // 1. Tạo Exchange (Topic Type)
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    // 2. Converter để gửi Object (DTO) thành JSON
    @Bean
    public Jackson2JsonMessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }

    // 3. Cấu hình RabbitTemplate sử dụng JSON Converter
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter());
        return template;
    }
    
    @Bean
    public Queue kitchenQueue() {
        return new Queue(QUEUE_KITCHEN);
    }

    @Bean
    public Binding bindingKitchen(Queue kitchenQueue, TopicExchange exchange) {
        return BindingBuilder.bind(kitchenQueue).to(exchange).with(ROUTING_KEY_KITCHEN);
    }
}