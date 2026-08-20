package com.tradex.notification.service;

import com.tradex.notification.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggingEmailNotificationService implements EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailNotificationService.class);

    @Override
    public void send(Notification notification) {
        log.info("[EMAIL NOTIFICATION SENT] User: {}, Type: {}, Title: '{}', Message: '{}'",
            notification.getUserId(), notification.getType(), notification.getTitle(), notification.getMessage());
    }
}
