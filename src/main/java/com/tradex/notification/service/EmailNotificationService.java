package com.tradex.notification.service;

import com.tradex.notification.entity.Notification;

public interface EmailNotificationService {
    void send(Notification notification);
}
