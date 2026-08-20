package com.tradex.order.repository;

import com.tradex.order.entity.Order;
import com.tradex.order.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUserIdAndClientOrderId(UUID userId, String clientOrderId);

    List<Order> findByUserId(UUID userId);

    List<Order> findByStatusInOrderByOrderSequenceAsc(List<OrderStatus> statuses);

    @Query("SELECT COALESCE(MAX(o.orderSequence), 0) FROM Order o")
    long findMaxOrderSequence();
}
