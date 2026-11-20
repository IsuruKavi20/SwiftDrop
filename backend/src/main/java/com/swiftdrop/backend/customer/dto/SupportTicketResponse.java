package com.swiftdrop.backend.customer.dto;

import com.swiftdrop.backend.customer.entity.SupportCategory;
import com.swiftdrop.backend.customer.entity.SupportMessage;
import com.swiftdrop.backend.customer.entity.SupportTicket;
import com.swiftdrop.backend.customer.entity.SupportTicketStatus;
import java.time.Instant;
import java.util.List;

public record SupportTicketResponse(Long id, SupportCategory category, SupportTicketStatus status,
                                    String subject, Long deliveryId, String trackingNumber,
                                    Instant createdAt, Instant updatedAt,
                                    List<Message> messages) {
    public record Message(Long id, String senderRole, String message, Instant createdAt) {
        public static Message from(SupportMessage item) {
            return new Message(item.getId(), item.getSenderRole(), item.getMessageText(), item.getCreatedAt());
        }
    }

    public static SupportTicketResponse from(SupportTicket ticket) {
        return new SupportTicketResponse(ticket.getId(), ticket.getCategory(), ticket.getStatus(),
                ticket.getSubject(), ticket.getDelivery() == null ? null : ticket.getDelivery().getId(),
                ticket.getDelivery() == null ? null : ticket.getDelivery().getTrackingNumber(),
                ticket.getCreatedAt(), ticket.getUpdatedAt(),
                ticket.getMessages().stream().map(Message::from).toList());
    }
}
