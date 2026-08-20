package com.tradex.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.portfolio.service.PortfolioService;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PortfolioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PortfolioService portfolioService;

    private String userToken;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        if (stockRepository.count() == 0) {
            stockRepository.saveAll(List.of(
                new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology")
            ));
        }

        RegisterRequest req = new RegisterRequest("portuser@tradex.com", "password123", "Portfolio User");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated()).andReturn();
        userToken = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        user = userRepository.findByEmail("portuser@tradex.com").orElseThrow();
        Stock stock = stockRepository.findBySymbol("AAPL").orElseThrow();
        portfolioService.addSharesOnBuy(user.getId(), stock.getId(), "AAPL", 50, new BigDecimal("180.0000"));
    }

    @Test
    @DisplayName("GET /api/portfolio — Returns user portfolio positions")
    void getUserPortfolioSuccess() throws Exception {
        mockMvc.perform(get("/api/portfolio")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$.data[0].quantity").value(50));
    }

    @Test
    @DisplayName("GET /api/portfolio/{symbol} — Returns position details for symbol")
    void getPositionBySymbolSuccess() throws Exception {
        mockMvc.perform(get("/api/portfolio/aapl")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.symbol").value("AAPL"))
            .andExpect(jsonPath("$.data.quantity").value(50))
            .andExpect(jsonPath("$.data.averageBuyPrice").value(180.0));
    }
}
