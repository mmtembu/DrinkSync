package com.smarteventbar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.config.GlobalExceptionHandler;
import com.smarteventbar.dto.CheckoutRequest;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.service.OrderService;
import com.smarteventbar.validation.PhoneNumberValidator;
import com.smarteventbar.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerCheckoutTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PhoneNumberValidator phoneNumberValidator;

    @InjectMocks
    private OrderController orderController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private CustomerOrder createMockOrder() {
        Station station = new Station();
        station.setId(1L);
        station.setName("Main Bar");
        CustomerSession session = new CustomerSession("session-123", station);
        CustomerOrder order = new CustomerOrder(station, session);
        order.setId(1L);
        order.setState(OrderState.AWAITING_PAYMENT);
        order.setTotalPrice(BigDecimal.valueOf(50.00));
        return order;
    }

    @Test
    void checkout_withValidPhoneAndOptIn_callsServiceWithPhoneAndOptIn() throws Exception {
        CheckoutRequest request = new CheckoutRequest("+27821234567", true);
        CustomerOrder mockOrder = createMockOrder();
        mockOrder.setCustomerPhone("+27821234567");
        mockOrder.setWhatsappOptIn(true);

        when(phoneNumberValidator.validate("+27821234567")).thenReturn(ValidationResult.success());
        when(orderService.checkout(eq(1L), eq("session-123"), eq("+27821234567"), eq(true)))
                .thenReturn(mockOrder);

        mockMvc.perform(post("/api/orders/1/checkout")
                        .header("X-Session-Id", "session-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(orderService).checkout(1L, "session-123", "+27821234567", true);
    }

    @Test
    void checkout_withInvalidPhoneFormat_returns400() throws Exception {
        CheckoutRequest request = new CheckoutRequest("invalid-phone", true);

        when(phoneNumberValidator.validate("invalid-phone"))
                .thenReturn(ValidationResult.failure("Phone number must start with '+' followed by country code and number"));

        mockMvc.perform(post("/api/orders/1/checkout")
                        .header("X-Session-Id", "session-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).checkout(anyLong(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void checkout_withNoRequestBody_callsServiceWithNulls() throws Exception {
        CustomerOrder mockOrder = createMockOrder();

        when(orderService.checkout(eq(1L), eq("session-123"), isNull(), isNull()))
                .thenReturn(mockOrder);

        mockMvc.perform(post("/api/orders/1/checkout")
                        .header("X-Session-Id", "session-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(orderService).checkout(1L, "session-123", null, null);
    }

    @Test
    void checkout_withOptInFalseAndNoPhone_callsServiceCorrectly() throws Exception {
        CheckoutRequest request = new CheckoutRequest(null, false);
        CustomerOrder mockOrder = createMockOrder();

        when(orderService.checkout(eq(1L), eq("session-123"), isNull(), eq(false)))
                .thenReturn(mockOrder);

        mockMvc.perform(post("/api/orders/1/checkout")
                        .header("X-Session-Id", "session-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(orderService).checkout(1L, "session-123", null, false);
    }

    @Test
    void checkout_withBlankPhone_skipsValidationAndCallsServiceWithNull() throws Exception {
        CheckoutRequest request = new CheckoutRequest("   ", true);
        CustomerOrder mockOrder = createMockOrder();

        when(orderService.checkout(eq(1L), eq("session-123"), eq("   "), eq(true)))
                .thenReturn(mockOrder);

        mockMvc.perform(post("/api/orders/1/checkout")
                        .header("X-Session-Id", "session-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Blank phone should not trigger validation
        verify(phoneNumberValidator, never()).validate(anyString());
    }

    @Test
    void checkout_withPhoneTooManyDigits_returns400WithDescriptiveError() throws Exception {
        CheckoutRequest request = new CheckoutRequest("+1234567890123456", true);

        when(phoneNumberValidator.validate("+1234567890123456"))
                .thenReturn(ValidationResult.failure("Phone number must not exceed 15 digits after '+' (got 16)"));

        mockMvc.perform(post("/api/orders/1/checkout")
                        .header("X-Session-Id", "session-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
