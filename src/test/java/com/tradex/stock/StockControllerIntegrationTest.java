package com.tradex.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradex.auth.dto.RegisterRequest;
import com.tradex.stock.dto.UpdateStockPriceRequest;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        // Seed default stocks if missing
        List<Stock> defaults = List.of(
            new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"),
            new Stock("MSFT", "Microsoft Corporation", new BigDecimal("420.7500"), new BigDecimal("418.9000"), "Technology"),
            new Stock("GOOGL", "Alphabet Inc.", new BigDecimal("175.2500"), new BigDecimal("174.8000"), "Communication"),
            new Stock("AMZN", "Amazon.com Inc.", new BigDecimal("182.0000"), new BigDecimal("181.1000"), "Consumer Cyclical"),
            new Stock("TSLA", "Tesla, Inc.", new BigDecimal("178.3000"), new BigDecimal("176.5000"), "Consumer Cyclical"),
            new Stock("NVDA", "NVIDIA Corporation", new BigDecimal("125.4000"), new BigDecimal("122.8000"), "Technology")
        );

        for (Stock s : defaults) {
            if (!stockRepository.existsBySymbol(s.getSymbol())) {
                stockRepository.save(s);
            }
        }

        // Register regular user
        RegisterRequest userReq = new RegisterRequest("stockuser@tradex.com", "password123", "Stock User");
        MvcResult userResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userReq)))
            .andExpect(status().isCreated())
            .andReturn();
        userToken = objectMapper.readTree(userResult.getResponse().getContentAsString())
            .path("data").path("accessToken").asText();

        // Create admin user directly in DB
        User admin = new User("adminuser@tradex.com", passwordEncoder.encode("password123"), "Admin User", Role.ROLE_ADMIN);
        userRepository.save(admin);

        // Login admin
        MvcResult adminResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"adminuser@tradex.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andReturn();
        adminToken = objectMapper.readTree(adminResult.getResponse().getContentAsString())
            .path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("GET /api/stocks authenticated returns 200 OK with seeded stocks")
    void getAllStocksAuthenticated() throws Exception {
        mockMvc.perform(get("/api/stocks")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(6)); // 6 seeded stocks
    }

    @Test
    @DisplayName("GET /api/stocks without JWT returns 401 Unauthorized")
    void getAllStocksUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/stocks"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/stocks/{symbol} lowercase symbol resolves correctly to 200 OK")
    void getStockBySymbolCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/stocks/aapl")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.symbol").value("AAPL"))
            .andExpect(jsonPath("$.data.companyName").value("Apple Inc."))
            .andExpect(jsonPath("$.data.currentPrice").value(185.5));
    }

    @Test
    @DisplayName("GET /api/stocks/{symbol} unknown symbol returns 404 Not Found")
    void getStockByUnknownSymbolReturns404() throws Exception {
        mockMvc.perform(get("/api/stocks/INVALID")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/stocks/{symbol}/price as ROLE_USER returns 403 Forbidden")
    void updatePriceAsUserReturns403() throws Exception {
        UpdateStockPriceRequest req = new UpdateStockPriceRequest(new BigDecimal("195.0000"));

        mockMvc.perform(patch("/api/stocks/AAPL/price")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /api/stocks/{symbol}/price as ROLE_ADMIN returns 200 OK and updates price")
    void updatePriceAsAdminReturns200() throws Exception {
        UpdateStockPriceRequest req = new UpdateStockPriceRequest(new BigDecimal("195.0000"));

        mockMvc.perform(patch("/api/stocks/AAPL/price")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.symbol").value("AAPL"))
            .andExpect(jsonPath("$.data.currentPrice").value(195.0));

        // Verify read endpoint returns updated price
        mockMvc.perform(get("/api/stocks/AAPL")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentPrice").value(195.0));
    }
}
