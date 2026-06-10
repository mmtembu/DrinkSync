package com.smarteventbar.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.dto.OrderItemRequest;
import com.smarteventbar.dto.StateTransitionRequest;
import com.smarteventbar.dto.VendorLoginRequest;
import com.smarteventbar.model.entity.*;
import com.smarteventbar.model.enums.OrderItemType;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.*;
import com.smarteventbar.service.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication, rate limiting, and observability.
 * <p>
 * Tests at the HTTP layer using MockMvc so that filters (JwtAuthFilter, RateLimitFilter)
 * and the GlobalExceptionHandler are exercised.
 * <p>
 * Uses @DirtiesContext to ensure the in-memory rate limit counters are reset after
 * this test class runs, preventing interference with other test classes.
 * <p>
 * Validates: Requirements 12.1, 12.3, 12.4, 17.1, 17.6, 20.1, 20.6
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthAndObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private SpiritItemRepository spiritItemRepository;

    @Autowired
    private MixerItemRepository mixerItemRepository;

    @Autowired
    private PremadeItemRepository premadeItemRepository;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Station station;
    private PremadeItem premade;
    private String accessCode;

    @BeforeEach
    void setUp() {
        // Clean up any leftover order data from previous test runs that might cause
        // visual_order_number collisions (MockMvc tests commit to DB permanently).
        // The visual order number prefix "AU" comes from "Auth Test Bar" station name.
        jdbcTemplate.update(
                "DELETE FROM idempotency_key WHERE order_id IN " +
                "(SELECT id FROM orders WHERE visual_order_number LIKE 'AU-%')");
        jdbcTemplate.update(
                "DELETE FROM order_state_history WHERE order_id IN " +
                "(SELECT id FROM orders WHERE visual_order_number LIKE 'AU-%')");
        jdbcTemplate.update(
                "DELETE FROM order_item WHERE order_id IN " +
                "(SELECT id FROM orders WHERE visual_order_number LIKE 'AU-%')");
        jdbcTemplate.update("DELETE FROM orders WHERE visual_order_number LIKE 'AU-%'");

        accessCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        station = stationRepository.save(
                new Station("Auth Test Bar", "Test Area", new BigDecimal("5.00"),
                        accessCode, 10));

        spiritItemRepository.save(
                new SpiritItem(station, "Vodka", new BigDecimal("30.00"), true));

        mixerItemRepository.save(
                new MixerItem(station, "Lemonade", new BigDecimal("10.00"), true));

        premade = premadeItemRepository.save(
                new PremadeItem(station, "Craft Beer", "Local IPA", new BigDecimal("45.00"), true));
    }

    @AfterEach
    void tearDown() {
        // MockMvc tests commit to the DB (not rolled back like @Transactional tests).
        // Clean up committed order data to prevent visual_order_number collisions on re-runs.
        // Delete in FK-safe order: idempotency_key → order_state_history → order_item → orders
        Long stationId = station.getId();
        jdbcTemplate.update(
                "DELETE FROM idempotency_key WHERE order_id IN (SELECT id FROM orders WHERE station_id = ?)",
                stationId);
        jdbcTemplate.update(
                "DELETE FROM order_state_history WHERE order_id IN (SELECT id FROM orders WHERE station_id = ?)",
                stationId);
        jdbcTemplate.update(
                "DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE station_id = ?)",
                stationId);
        jdbcTemplate.update("DELETE FROM orders WHERE station_id = ?", stationId);
    }

    // --- Helper methods ---

    private VendorLoginRequest loginRequest(Long stationId, String code) {
        VendorLoginRequest req = new VendorLoginRequest();
        req.setStationId(stationId);
        req.setAccessCode(code);
        return req;
    }

    private String loginAndGetToken() throws Exception {
        VendorLoginRequest loginReq = loginRequest(station.getId(), accessCode);
        MvcResult result = mockMvc.perform(post("/api/auth/vendor/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("token").asText();
    }

    private String premadeOrderItemsJson() throws Exception {
        OrderItemRequest req = new OrderItemRequest();
        req.setItemType(OrderItemType.PREMADE);
        req.setPremadeItemId(premade.getId());
        req.setQuantity(1);
        return objectMapper.writeValueAsString(List.of(req));
    }

    // -----------------------------------------------------------------------
    // Test 1: Vendor auth flow — login with valid code → get JWT → use for vendor endpoint
    // Validates: Req 12.1, 17.1
    // -----------------------------------------------------------------------

    @Test
    void vendorAuthFlow_loginGetJwtAndUseForVendorEndpoint() throws Exception {
        // Step 1: Login with valid access code → get JWT
        VendorLoginRequest loginReq = loginRequest(station.getId(), accessCode);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/vendor/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.stationId").value(station.getId()))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        // Step 2: Use JWT to access a vendor endpoint (GET station orders)
        mockMvc.perform(get("/api/stations/" + station.getId() + "/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // Test 2: Vendor auth with invalid code → 401
    // Validates: Req 17.1
    // -----------------------------------------------------------------------

    @Test
    void vendorAuth_invalidAccessCode_returns401() throws Exception {
        VendorLoginRequest loginReq = loginRequest(station.getId(), "WRONG1");

        mockMvc.perform(post("/api/auth/vendor/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // Test 3: Vendor endpoint without JWT → 401
    // Validates: Req 12.1
    // -----------------------------------------------------------------------

    @Test
    void vendorEndpoint_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/stations/" + station.getId() + "/orders"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // Test 4: Vendor endpoint with invalid JWT → 401
    // Validates: Req 12.1
    // -----------------------------------------------------------------------

    @Test
    void vendorEndpoint_withInvalidJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/stations/" + station.getId() + "/orders")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // Test 5: Vendor JWT used for state transition on order
    // Validates: Req 12.1
    // -----------------------------------------------------------------------

    @Test
    void vendorJwt_usedForOrderStateTransition() throws Exception {
        // Create a session
        CustomerSession session = sessionService.createSession(station.getId());
        String sessionId = session.getSessionId();

        // Create an order via MockMvc
        String itemsJson = premadeOrderItemsJson();
        MvcResult createResult = mockMvc.perform(post("/api/stations/" + station.getId() + "/orders")
                        .header("X-Session-Id", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Checkout via MockMvc
        mockMvc.perform(post("/api/orders/" + orderId + "/checkout")
                        .header("X-Session-Id", sessionId))
                .andExpect(status().isOk());

        // Pay via MockMvc
        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("X-Session-Id", sessionId)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAID"));

        // Get JWT
        String token = loginAndGetToken();

        // Use JWT to transition order state: PAID → PREPARING
        StateTransitionRequest transitionReq = new StateTransitionRequest();
        transitionReq.setTargetState(OrderState.PREPARING);

        mockMvc.perform(patch("/api/orders/" + orderId + "/state")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transitionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PREPARING"));
    }

    // -----------------------------------------------------------------------
    // Test 6: Auth rate limiting — 5 failed attempts → 6th returns 429
    // Validates: Req 17.6
    // -----------------------------------------------------------------------

    @Test
    void authRateLimiting_fiveFailedAttempts_thenRateLimited() throws Exception {
        // Use a dedicated station for this test to avoid interference
        String dedicatedCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Station rateLimitStation = stationRepository.save(
                new Station("Rate Limit Bar", "Test Area", new BigDecimal("5.00"),
                        dedicatedCode, 10));

        VendorLoginRequest badReq = loginRequest(rateLimitStation.getId(), "BADCOD");

        // 5 failed attempts — should all return 401
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/vendor/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt — should return 429 (rate limited)
        mockMvc.perform(post("/api/auth/vendor/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isTooManyRequests());
    }

    // -----------------------------------------------------------------------
    // Test 7: Order creation rate limiting — 5 orders then 6th returns 429
    // Validates: Req 12.3
    // -----------------------------------------------------------------------

    @Test
    void orderCreationRateLimiting_fiveOrders_thenRateLimited() throws Exception {
        // Create a session for this test
        CustomerSession session = sessionService.createSession(station.getId());
        String sessionId = session.getSessionId();
        String itemsJson = premadeOrderItemsJson();

        // Make 5 order creation requests. The rate limit filter records each request
        // BEFORE the controller runs, so even if the controller rejects some
        // (e.g., max concurrent orders after 3), the rate limit counter increments.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/stations/" + station.getId() + "/orders")
                    .header("X-Session-Id", sessionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(itemsJson));
        }

        // 6th request should be rate limited (429)
        mockMvc.perform(post("/api/stations/" + station.getId() + "/orders")
                        .header("X-Session-Id", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson))
                .andExpect(status().isTooManyRequests());
    }

    // -----------------------------------------------------------------------
    // Test 8: Payment rate limiting — 3 attempts then 4th returns 429
    // Validates: Req 12.4
    // -----------------------------------------------------------------------

    @Test
    void paymentRateLimiting_threeAttempts_thenRateLimited() throws Exception {
        // Create a session and an order via MockMvc
        CustomerSession session = sessionService.createSession(station.getId());
        String sessionId = session.getSessionId();
        String itemsJson = premadeOrderItemsJson();

        MvcResult createResult = mockMvc.perform(post("/api/stations/" + station.getId() + "/orders")
                        .header("X-Session-Id", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson))
                .andExpect(status().isCreated())
                .andReturn();

        Long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Checkout via MockMvc
        mockMvc.perform(post("/api/orders/" + orderId + "/checkout")
                        .header("X-Session-Id", sessionId))
                .andExpect(status().isOk());

        // Make 3 payment attempts — first succeeds (PAID), subsequent ones
        // may fail at the service level but the rate limit counter still increments
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                    .header("X-Session-Id", sessionId)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON));
        }

        // 4th attempt should be rate limited (429)
        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("X-Session-Id", sessionId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());
    }

    // -----------------------------------------------------------------------
    // Test 9: Health endpoint returns UP with DB status — accessible without auth
    // Validates: Req 20.1, 20.6
    // -----------------------------------------------------------------------

    @Test
    void healthEndpoint_returnsUpWithDbStatus_noAuthRequired() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db").exists())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    // -----------------------------------------------------------------------
    // Test 10: Flyway migrations apply cleanly (context loads successfully)
    // Validates: Req 20.1
    // -----------------------------------------------------------------------

    @Test
    void flywayMigrations_applyCleanly_contextLoads() {
        // If we reach this point, the Spring context loaded successfully,
        // which means Flyway migrations applied without errors and
        // JPA entity mappings are valid (ddl-auto: validate).
        // The station created in setUp() confirms DB operations work.
        org.junit.jupiter.api.Assertions.assertNotNull(station.getId(),
                "Station should be persisted, confirming Flyway migrations and JPA mappings are valid");
    }
}
