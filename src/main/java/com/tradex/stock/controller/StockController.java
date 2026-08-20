package com.tradex.stock.controller;

import com.tradex.common.dto.ApiResponse;
import com.tradex.stock.dto.StockPriceResponse;
import com.tradex.stock.dto.StockResponse;
import com.tradex.stock.dto.UpdateStockPriceRequest;
import com.tradex.stock.service.StockService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockResponse>>> getAllStocks() {
        List<StockResponse> stocks = stockService.getAllTradableStocks();
        return ResponseEntity.ok(ApiResponse.success("Stocks retrieved successfully", stocks));
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<StockResponse>> getStockBySymbol(@PathVariable String symbol) {
        StockResponse stock = stockService.getStockBySymbol(symbol);
        return ResponseEntity.ok(ApiResponse.success("Stock details retrieved successfully", stock));
    }

    @GetMapping("/{symbol}/price")
    public ResponseEntity<ApiResponse<StockPriceResponse>> getStockPrice(@PathVariable String symbol) {
        StockPriceResponse priceResponse = stockService.getCurrentPrice(symbol);
        return ResponseEntity.ok(ApiResponse.success("Stock price retrieved successfully", priceResponse));
    }

    @PatchMapping("/{symbol}/price")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StockResponse>> updateStockPrice(
        @PathVariable String symbol,
        @Valid @RequestBody UpdateStockPriceRequest request
    ) {
        StockResponse updatedStock = stockService.updateStockPrice(symbol, request.currentPrice());
        return ResponseEntity.ok(ApiResponse.success("Stock price updated successfully", updatedStock));
    }
}
