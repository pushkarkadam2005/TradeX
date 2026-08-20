package com.tradex.notification;

import com.tradex.notification.entity.Notification;
import com.tradex.notification.enums.NotificationStatus;
import com.tradex.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEntityTest {

    @Test
    @DisplayName("Notification Entity Initialization — Starts UNREAD and transitions to READ on markAsRead()")
    void notificationStateTransition() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Notification notification = new Notification(userId, NotificationType.ORDER_CREATED, "Order Placed", "Your order for AAPL was placed", eventId);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.getReadAt()).isNull();

        notification.markAsRead();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(notification.getReadAt()).isNotNull();
    }
}
