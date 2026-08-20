package com.tradex.portfolio.repository;

import com.tradex.portfolio.entity.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<PortfolioPosition, UUID> {

    List<PortfolioPosition> findByUserId(UUID userId);

    Optional<PortfolioPosition> findByUserIdAndStockId(UUID userId, UUID stockId);

    Optional<PortfolioPosition> findByUserIdAndSymbol(UUID userId, String symbol);
}
