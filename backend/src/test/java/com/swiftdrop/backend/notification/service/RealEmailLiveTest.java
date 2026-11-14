package com.swiftdrop.backend.notification.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Manual live verification test only – credentials should be provided via environment or local properties")
public class RealEmailLiveTest {

    @Autowired(required = false)
    private SmtpEmailSender emailSender;

    @Test
    void sendLiveTestEmail() {
        if (emailSender == null) {
            return;
        }
        emailSender.send(
                "swiftdroplk.delivery@gmail.com",
                "SwiftDrop Live Test Email via Brevo",
                "<div style='font-family:sans-serif;padding:24px;border:1px solid #e2e8f0;border-radius:12px;background:#fcfaf5;max-width:550px;'>" +
                "<h2 style='color:#3f2f1f;margin-bottom:12px;'>SwiftDrop Delivery Notification System</h2>" +
                "<p style='color:#4a5568;line-height:1.6;'>Congratulations! Your <strong>Brevo SMTP</strong> live email setup is now fully configured and verified.</p>" +
                "<p style='color:#4a5568;line-height:1.6;'>All delivery lifecycle events (CONFIRMED, PICKED_UP, IN_TRANSIT, DELIVERED) will now dispatch real HTML emails directly to customer inboxes.</p>" +
                "<div style='margin-top:20px;padding:12px 18px;background:#f4d58d;color:#3f2f1f;border-radius:8px;font-weight:bold;text-align:center;'>Status: Multi-Channel Email Dispatched Successfully</div>" +
                "</div>"
        );
    }
}
                "<h2 style='color:#3f2f1f;margin-bottom:12px;'>SwiftDrop Delivery Notification System</h2>" +
                "<p style='color:#4a5568;line-height:1.6;'>Congratulations! Your <strong>Brevo SMTP</strong> live email setup is now fully configured and verified.</p>" +
                "<p style='color:#4a5568;line-height:1.6;'>All delivery lifecycle events (CONFIRMED, PICKED_UP, IN_TRANSIT, DELIVERED) will now dispatch real HTML emails directly to customer inboxes.</p>" +
                "<div style='margin-top:20px;padding:12px 18px;background:#f4d58d;color:#3f2f1f;border-radius:8px;font-weight:bold;text-align:center;'>Status: Multi-Channel Email Dispatched Successfully</div>" +
                "</div>"
        );
    }
}
