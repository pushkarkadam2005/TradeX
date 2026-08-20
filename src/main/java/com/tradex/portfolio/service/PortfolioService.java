package com.tradex.portfolio.service;

import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.ResourceNotFoundException;
import com.tradex.portfolio.dto.PortfolioPositionResponse;
import com.tradex.portfolio.entity.PortfolioPosition;
import com.tradex.portfolio.repository.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @Transactional
    public PortfolioPosition getOrCreatePositionEntity(UUID userId, UUID stockId, String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        return portfolioRepository.findByUserIdAndStockId(userId, stockId)
            .orElseGet(() -> portfolioRepository.save(new PortfolioPosition(userId, stockId, normalizedSymbol)));
    }

    @Transactional(readOnly = true)
    public List<PortfolioPositionResponse> getUserPortfolio(UUID userId) {
        return portfolioRepository.findByUserId(userId).stream()
            .map(PortfolioPositionResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioPositionResponse getPositionBySymbol(UUID userId, String symbol) {
        PortfolioPosition position = portfolioRepository.findByUserIdAndSymbol(userId, symbol.trim().toUpperCase())
            .orElseThrow(() -> new ResourceNotFoundException("PortfolioPosition", "symbol", symbol));
        return PortfolioPositionResponse.fromEntity(position);
    }

    @Transactional
    public void reserveShares(UUID userId, UUID stockId, String symbol, long quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleViolationException("INVALID_SHARE_QUANTITY", "Share reservation quantity must be strictly positive");
        }

        PortfolioPosition position = getOrCreatePositionEntity(userId, stockId, symbol);
        if (position.getAvailableQuantity() < quantity) {
            throw new BusinessRuleViolationException("INSUFFICIENT_SHARES",
                "Insufficient available shares for " + symbol + ". Requested: " + quantity + ", Available: " + position.getAvailableQuantity());
        }

        position.reserveShares(quantity);
        portfolioRepository.save(position);
    }

    @Transactional
    public void releaseShares(UUID userId, UUID stockId, long quantity) {
        if (quantity <= 0) {
            return;
        }

        portfolioRepository.findByUserIdAndStockId(userId, stockId).ifPresent(position -> {
            position.releaseShares(quantity);
            portfolioRepository.save(position);
        });
    }

    @Transactional
    public void deductLockedShares(UUID userId, UUID stockId, long quantity) {
        if (quantity <= 0) {
            return;
        }

        portfolioRepository.findByUserIdAndStockId(userId, stockId).ifPresent(position -> {
            position.deductLockedShares(quantity);
            portfolioRepository.save(position);
        });
    }

    @Transactional
    public void addSharesOnBuy(UUID userId, UUID stockId, String symbol, long fillQty, BigDecimal fillPrice) {
        if (fillQty <= 0) {
            return;
        }

        PortfolioPosition position = getOrCreatePositionEntity(userId, stockId, symbol);
        position.addSharesOnBuy(fillQty, fillPrice);
        portfolioRepository.save(position);
    }
}
