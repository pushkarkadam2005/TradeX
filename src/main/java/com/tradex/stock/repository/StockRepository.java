package com.tradex.stock.repository;

import com.tradex.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {

    Optional<Stock> findBySymbol(String symbol);

    List<Stock> findByTradableTrue();

    boolean existsBySymbol(String symbol);
}
