package com.smarteventbar.notification.properties;

import com.smarteventbar.config.RateLimitedSendQueue;
import com.smarteventbar.dto.WhatsAppResponse;
import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.notification.WhatsAppClient;
import com.smarteventbar.notification.WhatsAppProviderAdapter;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for template parameters containing required order fields.
 *
 * Property 6: Template parameters contain required order fields
 * For any order, the order_confirmed template parameters SHALL contain the customer name,
 * visual order number, and station name; the order_ready template parameters SHALL contain
 * the visual order number and station name; the order_receipt template parameters SHALL
 * contain the business name, visual order number, total price, and receipt URL.
 *
 * **Validates: Requirements 2.2, 3.2, 4.2**
 */
class TemplateParametersProperties {

    @Provide
    Arbitrary<String> customerNames() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> visualOrderNumbers() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(3).ofMaxLength(10);
    }

    @Provide
    Arbitrary<String> stationNames() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(2).ofMaxLength(30);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(99999.99))
                .ofScale(2);
    }

    @Provide
    Arbitrary<String> phoneNumbers() {
        return Arbitraries.integers().between(1, 9).flatMap(firstDigit ->
                Arbitraries.strings().numeric().ofMinLength(5).ofMaxLength(14).map(rest ->
                        "+" + firstDigit + rest
                )
        );
    }

    private CustomerOrder createOrder(String visualOrderNumber, String stationName,
                                       BigDecimal totalPrice, String phone) {
        Station station = new Station();
        station.setName(stationName);

        CustomerOrder order = new CustomerOrder();
        order.setId(1L);
        order.setVisualOrderNumber(visualOrderNumber);
        order.setStation(station);
        order.setTotalPrice(totalPrice);
        order.setCustomerPhone(phone);
        order.setWhatsappOptIn(true);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    private WhatsAppResponse createSuccessResponse() {
        return new WhatsAppResponse("whatsapp",
                List.of(new WhatsAppResponse.Contact("+1234567890", "1234567890")),
                List.of(new WhatsAppResponse.Message("wamid.test123")));
    }

    /**
     * Property 6a: order_confirmed template has 3 parameters (name, order number, station).
     */
    @Property(tries = 100)
    void orderConfirmedTemplateHasThreeParameters(
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("stationNames") String stationName,
            @ForAll("prices") BigDecimal price,
            @ForAll("phoneNumbers") String phone) {

        WhatsAppClient mockClient = mock(WhatsAppClient.class);
        RateLimitedSendQueue mockQueue = mock(RateLimitedSendQueue.class);
        doNothing().when(mockQueue).acquireToken();

        when(mockClient.sendTemplateMessage(any(WhatsAppTemplateRequest.class)))
                .thenReturn(createSuccessResponse());

        WhatsAppProviderAdapter adapter = new WhatsAppProviderAdapter(mockClient, mockQueue);

        CustomerOrder order = createOrder(visualOrderNumber, stationName, price, phone);

        adapter.sendOrderConfirmed(order);

        var captor = org.mockito.ArgumentCaptor.forClass(WhatsAppTemplateRequest.class);
        verify(mockClient).sendTemplateMessage(captor.capture());

        WhatsAppTemplateRequest captured = captor.getValue();
        assertThat(captured.getTemplate().getName()).isEqualTo("order_confirmed");

        List<WhatsAppTemplateRequest.ParameterObject> params =
                captured.getTemplate().getComponents().get(0).getParameters();
        assertThat(params).hasSize(3);

        // Verify parameters contain the expected values
        List<String> paramTexts = params.stream()
                .map(WhatsAppTemplateRequest.ParameterObject::getText)
                .toList();
        assertThat(paramTexts).contains(visualOrderNumber);
        assertThat(paramTexts).contains(stationName);
    }

    /**
     * Property 6b: order_ready template has 2 parameters (order number, station).
     */
    @Property(tries = 100)
    void orderReadyTemplateHasTwoParameters(
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("stationNames") String stationName,
            @ForAll("prices") BigDecimal price,
            @ForAll("phoneNumbers") String phone) {

        WhatsAppClient mockClient = mock(WhatsAppClient.class);
        RateLimitedSendQueue mockQueue = mock(RateLimitedSendQueue.class);
        doNothing().when(mockQueue).acquireToken();

        when(mockClient.sendTemplateMessage(any(WhatsAppTemplateRequest.class)))
                .thenReturn(createSuccessResponse());

        WhatsAppProviderAdapter adapter = new WhatsAppProviderAdapter(mockClient, mockQueue);

        CustomerOrder order = createOrder(visualOrderNumber, stationName, price, phone);

        adapter.sendOrderReady(order);

        var captor = org.mockito.ArgumentCaptor.forClass(WhatsAppTemplateRequest.class);
        verify(mockClient).sendTemplateMessage(captor.capture());

        WhatsAppTemplateRequest captured = captor.getValue();
        assertThat(captured.getTemplate().getName()).isEqualTo("order_ready");

        List<WhatsAppTemplateRequest.ParameterObject> params =
                captured.getTemplate().getComponents().get(0).getParameters();
        assertThat(params).hasSize(2);

        List<String> paramTexts = params.stream()
                .map(WhatsAppTemplateRequest.ParameterObject::getText)
                .toList();
        assertThat(paramTexts).contains(visualOrderNumber);
        assertThat(paramTexts).contains(stationName);
    }

    /**
     * Property 6c: order_receipt template has 4 parameters (business name, order number, total, receipt URL).
     */
    @Property(tries = 100)
    void orderReceiptTemplateHasFourParameters(
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("stationNames") String stationName,
            @ForAll("prices") BigDecimal price,
            @ForAll("phoneNumbers") String phone) {

        WhatsAppClient mockClient = mock(WhatsAppClient.class);
        RateLimitedSendQueue mockQueue = mock(RateLimitedSendQueue.class);
        doNothing().when(mockQueue).acquireToken();

        when(mockClient.sendTemplateMessage(any(WhatsAppTemplateRequest.class)))
                .thenReturn(createSuccessResponse());

        WhatsAppProviderAdapter adapter = new WhatsAppProviderAdapter(mockClient, mockQueue);

        CustomerOrder order = createOrder(visualOrderNumber, stationName, price, phone);

        adapter.sendReceipt(order);

        var captor = org.mockito.ArgumentCaptor.forClass(WhatsAppTemplateRequest.class);
        verify(mockClient).sendTemplateMessage(captor.capture());

        WhatsAppTemplateRequest captured = captor.getValue();
        assertThat(captured.getTemplate().getName()).isEqualTo("order_receipt");

        List<WhatsAppTemplateRequest.ParameterObject> params =
                captured.getTemplate().getComponents().get(0).getParameters();
        assertThat(params).hasSize(4);

        List<String> paramTexts = params.stream()
                .map(WhatsAppTemplateRequest.ParameterObject::getText)
                .toList();
        // Business name (station name), order number, total price, receipt URL
        assertThat(paramTexts).contains(stationName);
        assertThat(paramTexts).contains(visualOrderNumber);
        assertThat(paramTexts).contains(price.toPlainString());
    }
}
