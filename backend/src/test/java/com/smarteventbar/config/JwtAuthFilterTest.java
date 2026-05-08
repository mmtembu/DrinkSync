package com.smarteventbar.config;

import com.smarteventbar.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private AuthService authService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(authService);
    }

    // --- Helper methods ---

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod(method);
        req.setRequestURI(uri);
        return req;
    }

    private void doFilter(MockHttpServletRequest request, MockHttpServletResponse response)
            throws ServletException, IOException {
        filter.doFilter(request, response, filterChain);
    }

    // --- Customer endpoints (should bypass filter) ---

    @Nested
    @DisplayName("Customer endpoints bypass authentication")
    class CustomerEndpoints {

        @Test
        @DisplayName("GET /api/stations/{id} passes through without auth")
        void getStationDetails() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/stations/1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("GET /api/stations/{id}/menu passes through without auth")
        void getStationMenu() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/stations/42/menu");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("POST /api/stations/{id}/orders passes through without auth")
        void createOrder() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/stations/1/orders");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("GET /api/orders/{id} passes through without auth")
        void getOrder() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/orders/99");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("PUT /api/orders/{id}/items passes through without auth")
        void updateOrderItems() throws Exception {
            MockHttpServletRequest req = request("PUT", "/api/orders/99/items");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("POST /api/orders/{id}/checkout passes through without auth")
        void checkout() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/orders/99/checkout");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("POST /api/orders/{id}/pay passes through without auth")
        void pay() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/orders/99/pay");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("POST /api/orders/{id}/cancel passes through without auth")
        void cancel() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/orders/99/cancel");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("POST /api/sessions passes through without auth")
        void createSession() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/sessions");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("GET /api/sessions/{id} passes through without auth")
        void getSession() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/sessions/abc-123");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("POST /api/auth/vendor/login passes through without auth")
        void vendorLogin() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/auth/vendor/login");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }

        @Test
        @DisplayName("GET /actuator/health passes through without auth")
        void actuatorHealth() throws Exception {
            MockHttpServletRequest req = request("GET", "/actuator/health");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
        }
    }

    // --- Vendor endpoints (require authentication) ---

    @Nested
    @DisplayName("Vendor endpoints require authentication")
    class VendorEndpoints {

        @Test
        @DisplayName("Missing Authorization header returns 401 with 'Authentication required'")
        void missingAuthHeader() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/stations/1/spirits");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
            assertThat(res.getContentAsString()).contains("Authentication required");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Malformed Authorization header (no Bearer prefix) returns 401")
        void malformedAuthHeader() throws Exception {
            MockHttpServletRequest req = request("PUT", "/api/stations/1/cup-price");
            req.addHeader("Authorization", "Token some-token");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
            assertThat(res.getContentAsString()).contains("Authentication required");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Invalid JWT token returns 401 with re-authentication message")
        void invalidToken() throws Exception {
            when(authService.validateToken("bad-token")).thenReturn(false);

            MockHttpServletRequest req = request("PATCH", "/api/orders/5/state");
            req.addHeader("Authorization", "Bearer bad-token");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
            assertThat(res.getContentAsString()).contains("Token expired, re-authentication required");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Valid JWT token allows request through and sets stationId attribute")
        void validToken() throws Exception {
            when(authService.validateToken("good-token")).thenReturn(true);
            when(authService.getStationIdFromToken("good-token")).thenReturn(42L);

            MockHttpServletRequest req = request("GET", "/api/stations/42/orders");
            req.addHeader("Authorization", "Bearer good-token");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            verify(filterChain).doFilter(req, res);
            assertThat(req.getAttribute(JwtAuthFilter.STATION_ID_ATTRIBUTE)).isEqualTo(42L);
        }
    }

    // --- Vendor route matching ---

    @Nested
    @DisplayName("Vendor route matching covers all protected endpoints")
    class VendorRouteMatching {

        @Test
        @DisplayName("POST /api/stations/{id}/spirits requires auth")
        void createSpirit() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/stations/1/spirits");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("PUT /api/stations/{id}/spirits/{itemId} requires auth")
        void updateSpirit() throws Exception {
            MockHttpServletRequest req = request("PUT", "/api/stations/1/spirits/5");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("DELETE /api/stations/{id}/spirits/{itemId} requires auth")
        void deleteSpirit() throws Exception {
            MockHttpServletRequest req = request("DELETE", "/api/stations/1/spirits/5");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("PATCH /api/stations/{id}/spirits/{itemId}/availability requires auth")
        void toggleSpiritAvailability() throws Exception {
            MockHttpServletRequest req = request("PATCH", "/api/stations/1/spirits/5/availability");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("POST /api/stations/{id}/mixers requires auth")
        void createMixer() throws Exception {
            MockHttpServletRequest req = request("POST", "/api/stations/1/mixers");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("PUT /api/stations/{id}/mixers/{itemId} requires auth")
        void updateMixer() throws Exception {
            MockHttpServletRequest req = request("PUT", "/api/stations/1/mixers/3");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("DELETE /api/stations/{id}/premades/{itemId} requires auth")
        void deletePremade() throws Exception {
            MockHttpServletRequest req = request("DELETE", "/api/stations/1/premades/7");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("PUT /api/stations/{id}/cup-price requires auth")
        void setCupPrice() throws Exception {
            MockHttpServletRequest req = request("PUT", "/api/stations/1/cup-price");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("PUT /api/stations/{id}/pickup-window requires auth")
        void setPickupWindow() throws Exception {
            MockHttpServletRequest req = request("PUT", "/api/stations/1/pickup-window");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("PATCH /api/orders/{id}/state requires auth")
        void transitionOrderState() throws Exception {
            MockHttpServletRequest req = request("PATCH", "/api/orders/10/state");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("GET /api/stations/{id}/orders requires auth")
        void getStationOrders() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/stations/1/orders");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("GET /api/stations/{id}/menu does NOT require auth (different from /orders)")
        void getMenuIsNotProtected() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/stations/1/menu");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            // Should pass through — not a vendor endpoint
            verify(filterChain).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    // --- Response format ---

    @Nested
    @DisplayName("Response format")
    class ResponseFormat {

        @Test
        @DisplayName("401 response has application/json content type")
        void jsonContentType() throws Exception {
            MockHttpServletRequest req = request("PATCH", "/api/orders/1/state");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getContentType()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("Missing token error body is valid JSON")
        void missingTokenJsonBody() throws Exception {
            MockHttpServletRequest req = request("GET", "/api/stations/1/orders");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getContentAsString()).isEqualTo("{\"error\":\"Authentication required\"}");
        }

        @Test
        @DisplayName("Invalid token error body is valid JSON")
        void invalidTokenJsonBody() throws Exception {
            when(authService.validateToken("expired")).thenReturn(false);

            MockHttpServletRequest req = request("PUT", "/api/stations/1/cup-price");
            req.addHeader("Authorization", "Bearer expired");
            MockHttpServletResponse res = new MockHttpServletResponse();

            doFilter(req, res);

            assertThat(res.getContentAsString())
                    .isEqualTo("{\"error\":\"Token expired, re-authentication required\"}");
        }
    }
}
