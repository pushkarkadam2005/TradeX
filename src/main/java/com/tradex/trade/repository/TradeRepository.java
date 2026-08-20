package com.tradex.trade.repository;

import com.tradex.trade.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TradeRepository extends JpaRepository<Trade, UUID> {

    Optional<Trade> findByExecutionId(String executionId);

    boolean existsByExecutionId(String executionId);

    List<Trade> findBySymbol(String symbol);

    List<Trade> findByBuyerIdOrSellerId(UUID buyerId, UUID sellerId);
}
