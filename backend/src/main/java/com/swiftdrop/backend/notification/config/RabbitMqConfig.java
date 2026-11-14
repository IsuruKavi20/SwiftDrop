package com.swiftdrop.backend.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_SWIFTDROP = "swiftdrop.exchange";
    public static final String EXCHANGE_DLX = "swiftdrop.dlx";

    public static final String QUEUE_DELIVERY_NOTIFICATIONS = "swiftdrop.delivery.notifications";
    public static final String QUEUE_COURIER_ASSIGNMENTS = "swiftdrop.courier.assignments";
    public static final String QUEUE_MERCHANT_WEBHOOKS = "swiftdrop.merchant.webhooks";
    public static final String QUEUE_DEAD_LETTER = "swiftdrop.dead-letter";

    public static final String ROUTING_KEY_STATUS = "delivery.status.#";
    public static final String ROUTING_KEY_ASSIGNMENT = "delivery.courier.assigned";
    public static final String ROUTING_KEY_WEBHOOK = "merchant.webhook.#";
    public static final String ROUTING_KEY_DEAD_LETTER = "swiftdrop.dlq";

    public static final String TRUSTED_EVENT_PACKAGE = "com.swiftdrop.backend.notification.event";

    @Bean
    public TopicExchange swiftdropExchange() {
        return new TopicExchange(EXCHANGE_SWIFTDROP, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue deliveryNotificationQueue() {
        return QueueBuilder.durable(QUEUE_DELIVERY_NOTIFICATIONS)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue courierAssignmentQueue() {
        return QueueBuilder.durable(QUEUE_COURIER_ASSIGNMENTS)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue merchantWebhookQueue() {
        return QueueBuilder.durable(QUEUE_MERCHANT_WEBHOOKS)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DEAD_LETTER).build();
    }

    @Bean
    public Binding bindingDeliveryNotifications(Queue deliveryNotificationQueue, TopicExchange swiftdropExchange) {
        return BindingBuilder.bind(deliveryNotificationQueue).to(swiftdropExchange).with(ROUTING_KEY_STATUS);
    }

    @Bean
    public Binding bindingCourierAssignment(Queue courierAssignmentQueue, TopicExchange swiftdropExchange) {
        return BindingBuilder.bind(courierAssignmentQueue).to(swiftdropExchange).with(ROUTING_KEY_ASSIGNMENT);
    }

    @Bean
    public Binding bindingMerchantWebhooks(Queue merchantWebhookQueue, TopicExchange swiftdropExchange) {
        return BindingBuilder.bind(merchantWebhookQueue).to(swiftdropExchange).with(ROUTING_KEY_WEBHOOK);
    }

    @Bean
    public Binding bindingDeadLetter(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY_DEAD_LETTER);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}

