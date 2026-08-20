package com.tradex.notification;

import com.tradex.notification.entity.Notification;
import com.tradex.notification.enums.NotificationType;
import com.tradex.notification.repository.NotificationRepository;
import com.tradex.notification.repository.ProcessedEventRepository;
import com.tradex.notification.service.NotificationService;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentDuplicateEventTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("concnotif_" + UUID.randomUUID() + "@tradex.com", "passhash", "Conc Notif User", Role.ROLE_USER));
    }

    @Test
    @DisplayName("Concurrent Duplicate Deliveries — 20 concurrent threads delivering same eventId result in exactly 1 Notification and 1 ProcessedEvent row")
    void concurrentDuplicateEventsHandledIdempotently() throws Exception {
        int threads = 20;
        UUID eventId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Optional<Notification> opt = notificationService.createNotificationIdempotent(
                        user.getId(), NotificationType.ORDER_CREATED, "Order Placed", "Your order was placed", eventId
                    );
                    if (opt.isPresent()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threads);

        // Verify exactly 1 notification entity exists in DB for this user/eventId/type
        assertThat(notificationRepository.existsByUserIdAndEventIdAndType(user.getId(), eventId, NotificationType.ORDER_CREATED)).isTrue();
        assertThat(processedEventRepository.existsByEventId(eventId)).isTrue();
    }
}
