# Merchant recipient email

The authenticated Merchant booking endpoint (`POST /api/merchants/me/deliveries`) accepts an optional top-level `recipientEmail` string. The booking and owned order-detail responses (`GET /api/merchants/me/deliveries/{id}`) return the same field. Omitted or blank email values are stored as null. The shared Merchant booking DTO also accepts the field on the machine booking endpoint when that endpoint is enabled.

The address is stored in `delivery.recipient_email` and available through `Delivery.getRecipientEmail()`. The notification workflow can read it from the persisted delivery using the delivery ID already carried by status events. The current status-event schema and notification recipients are unchanged.

Before sending recipient emails, agree on eligible status transitions and templates with the Notification team. Use the saved recipient address separately from the customer account email. Skip recipient email when absent and keep it out of public tracking, Merchant webhook payloads, and general logs.
