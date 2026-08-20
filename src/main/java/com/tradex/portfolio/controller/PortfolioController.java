package com.tradex.portfolio.controller;

import com.tradex.auth.security.UserPrincipal;
import com.tradex.common.dto.ApiResponse;
import com.tradex.portfolio.dto.PortfolioPositionResponse;
import com.tradex.portfolio.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PortfolioPositionResponse>>> getUserPortfolio(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<PortfolioPositionResponse> portfolio = portfolioService.getUserPortfolio(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Portfolio retrieved successfully", portfolio));
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<PortfolioPositionResponse>> getPositionBySymbol(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable String symbol
    ) {
        PortfolioPositionResponse position = portfolioService.getPositionBySymbol(principal.getId(), symbol);
        return ResponseEntity.ok(ApiResponse.success("Position retrieved successfully", position));
    }
}
