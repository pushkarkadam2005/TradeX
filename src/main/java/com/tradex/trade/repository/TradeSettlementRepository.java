package com.tradex.trade.repository;

import com.tradex.trade.entity.TradeSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TradeSettlementRepository extends JpaRepository<TradeSettlement, UUID> {

    boolean existsByExecutionId(String executionId);

    Optional<TradeSettlement> findByExecutionId(String executionId);
}
