package com.swiftdrop.backend.notification.service;

import com.swiftdrop.backend.notification.config.RabbitMqConfig;
import com.swiftdrop.backend.notification.event.MerchantAssignmentWebhook;
import com.swiftdrop.backend.notification.event.MerchantConfirmedWebhook;
import com.swiftdrop.backend.notification.event.MerchantLifecycleWebhook;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RabbitListener(queues = RabbitMqConfig.QUEUE_MERCHANT_WEBHOOKS)
public class MerchantWebhookListener {
    private final WebhookDispatcherService dispatcher;
    private final ObjectMapper mapper;

    public MerchantWebhookListener(WebhookDispatcherService dispatcher, ObjectMapper mapper) {
        this.dispatcher = dispatcher;
        this.mapper = mapper;
    }

    @RabbitHandler
    public void handle(MerchantConfirmedWebhook event) {
        var payload = event.payload();
        dispatcher.dispatchWebhook(event.merchantId(), payload.deliveryId(), payload.status(),
                mapper.writeValueAsString(payload), payload.eventId());
    }

    @RabbitHandler
    public void handle(MerchantAssignmentWebhook event) {
        var payload = event.payload();
        dispatcher.dispatchWebhook(event.merchantId(), payload.deliveryId(), payload.eventType(),
                mapper.writeValueAsString(payload), payload.eventId());
    }

    @RabbitHandler
    public void handle(MerchantLifecycleWebhook event) {
        var payload = event.payload();
        dispatcher.dispatchWebhook(event.merchantId(), payload.deliveryId(), payload.status(),
                mapper.writeValueAsString(payload), payload.eventId());
    }

    @RabbitHandler(isDefault = true)
    public void rejectUnsupported(Object message) {
        throw new AmqpRejectAndDontRequeueException("Unsupported Merchant webhook message");
    }
}
