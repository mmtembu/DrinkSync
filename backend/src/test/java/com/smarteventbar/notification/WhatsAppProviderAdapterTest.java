package com.smarteventbar.notification;

import com.smarteventbar.config.RateLimitedSendQueue;
import com.smarteventbar.dto.WhatsAppResponse;
import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppProviderAdapterTest {

    @Mock
    private WhatsAppClient whatsAppClient;

    @Mock
    private RateLimitedSendQueue rateLimitedSendQueue;

    private WhatsAppProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WhatsAppProviderAdapter(whatsAppClient, rateLimitedSendQueue);
    }

    @Test
    void sendOrderConfirmed_buildsCorrectTemplateWithNameOrderNumberAndStation() {
        CustomerOrder order = createTestOrder();
        WhatsAppResponse response = createSuccessResponse("wamid.123");
        when(whatsAppClient.sendTemplateMessage(any())).thenReturn(response);

        String messageId = adapter.sendOrderConfirmed(order);

        assertThat(messageId).isEqualTo("wamid.123");

        ArgumentCaptor<WhatsAppTemplateRequest> captor = ArgumentCaptor.forClass(WhatsAppTemplateRequest.class);
        verify(whatsAppClient).sendTemplateMessage(captor.capture());
        verify(rateLimitedSendQueue).acquireToken();

        WhatsAppTemplateRequest request = captor.getValue();
        assertThat(request.getTo()).isEqualTo("+27123456789");
        assertThat(request.getTemplate().getName()).isEqualTo("order_confirmed");
        assertThat(request.getTemplate().getLanguage().getCode()).isEqualTo("en");

        List<WhatsAppTemplateRequest.ParameterObject> params =
                request.getTemplate().getComponents().get(0).getParameters();
        assertThat(params).hasSize(3);
        assertThat(params.get(0).getText()).isEqualTo("Customer");
        assertThat(params.get(1).getText()).isEqualTo("ORD-001");
        assertThat(params.get(2).getText()).isEqualTo("Main Bar");
    }

    @Test
    void sendOrderReady_buildsCorrectTemplateWithOrderNumberAndStation() {
        CustomerOrder order = createTestOrder();
        WhatsAppResponse response = createSuccessResponse("wamid.456");
        when(whatsAppClient.sendTemplateMessage(any())).thenReturn(response);

        String messageId = adapter.sendOrderReady(order);

        assertThat(messageId).isEqualTo("wamid.456");

        ArgumentCaptor<WhatsAppTemplateRequest> captor = ArgumentCaptor.forClass(WhatsAppTemplateRequest.class);
        verify(whatsAppClient).sendTemplateMessage(captor.capture());

        WhatsAppTemplateRequest request = captor.getValue();
        assertThat(request.getTo()).isEqualTo("+27123456789");
        assertThat(request.getTemplate().getName()).isEqualTo("order_ready");
        assertThat(request.getTemplate().getLanguage().getCode()).isEqualTo("en");

        List<WhatsAppTemplateRequest.ParameterObject> params =
                request.getTemplate().getComponents().get(0).getParameters();
        assertThat(params).hasSize(2);
        assertThat(params.get(0).getText()).isEqualTo("ORD-001");
        assertThat(params.get(1).getText()).isEqualTo("Main Bar");
    }

    @Test
    void sendReceipt_buildsCorrectTemplateWithBusinessNameOrderNumberTotalAndReceiptUrl() {
        CustomerOrder order = createTestOrder();
        WhatsAppResponse response = createSuccessResponse("wamid.789");
        when(whatsAppClient.sendTemplateMessage(any())).thenReturn(response);

        String messageId = adapter.sendReceipt(order);

        assertThat(messageId).isEqualTo("wamid.789");

        ArgumentCaptor<WhatsAppTemplateRequest> captor = ArgumentCaptor.forClass(WhatsAppTemplateRequest.class);
        verify(whatsAppClient).sendTemplateMessage(captor.capture());

        WhatsAppTemplateRequest request = captor.getValue();
        assertThat(request.getTo()).isEqualTo("+27123456789");
        assertThat(request.getTemplate().getName()).isEqualTo("order_receipt");
        assertThat(request.getTemplate().getLanguage().getCode()).isEqualTo("en");

        List<WhatsAppTemplateRequest.ParameterObject> params =
                request.getTemplate().getComponents().get(0).getParameters();
        assertThat(params).hasSize(4);
        assertThat(params.get(0).getText()).isEqualTo("Main Bar");
        assertThat(params.get(1).getText()).isEqualTo("ORD-001");
        assertThat(params.get(2).getText()).isEqualTo("150.00");
        assertThat(params.get(3).getText()).isEqualTo("/orders/ORD-001/receipt");
    }

    @Test
    void sendSupportReply_buildsCorrectTemplateWithFormattedOrderList() {
        CustomerOrder order1 = createTestOrderWithState("ORD-001", OrderState.PAID);
        CustomerOrder order2 = createTestOrderWithState("ORD-002", OrderState.READY);
        WhatsAppResponse response = createSuccessResponse("wamid.support");
        when(whatsAppClient.sendTemplateMessage(any())).thenReturn(response);

        String messageId = adapter.sendSupportReply("+27123456789", List.of(order1, order2));

        assertThat(messageId).isEqualTo("wamid.support");

        ArgumentCaptor<WhatsAppTemplateRequest> captor = ArgumentCaptor.forClass(WhatsAppTemplateRequest.class);
        verify(whatsAppClient).sendTemplateMessage(captor.capture());

        WhatsAppTemplateRequest request = captor.getValue();
        assertThat(request.getTo()).isEqualTo("+27123456789");
        assertThat(request.getTemplate().getName()).isEqualTo("support_reply");
        assertThat(request.getTemplate().getLanguage().getCode()).isEqualTo("en");

        List<WhatsAppTemplateRequest.ParameterObject> params =
                request.getTemplate().getComponents().get(0).getParameters();
        assertThat(params).hasSize(1);
        assertThat(params.get(0).getText()).contains("#ORD-001 - PAID");
        assertThat(params.get(0).getText()).contains("#ORD-002 - READY");
    }

    @Test
    void sendSupportReply_emptyOrderList_returnsNoActiveOrdersMessage() {
        WhatsAppResponse response = createSuccessResponse("wamid.empty");
        when(whatsAppClient.sendTemplateMessage(any())).thenReturn(response);

        adapter.sendSupportReply("+27123456789", List.of());

        ArgumentCaptor<WhatsAppTemplateRequest> captor = ArgumentCaptor.forClass(WhatsAppTemplateRequest.class);
        verify(whatsAppClient).sendTemplateMessage(captor.capture());

        WhatsAppTemplateRequest request = captor.getValue();
        List<WhatsAppTemplateRequest.ParameterObject> params =
                request.getTemplate().getComponents().get(0).getParameters();
        assertThat(params.get(0).getText()).isEqualTo("No active orders found.");
    }

    @Test
    void isEnabled_returnsTrue() {
        assertThat(adapter.isEnabled()).isTrue();
    }

    @Test
    void sendOrderConfirmed_acquiresTokenBeforeSending() {
        CustomerOrder order = createTestOrder();
        WhatsAppResponse response = createSuccessResponse("wamid.token");
        when(whatsAppClient.sendTemplateMessage(any())).thenReturn(response);

        adapter.sendOrderConfirmed(order);

        verify(rateLimitedSendQueue).acquireToken();
        verify(whatsAppClient).sendTemplateMessage(any());
    }

    private CustomerOrder createTestOrder() {
        Station station = new Station();
        station.setName("Main Bar");

        CustomerOrder order = new CustomerOrder();
        order.setCustomerPhone("+27123456789");
        order.setVisualOrderNumber("ORD-001");
        order.setStation(station);
        order.setTotalPrice(new BigDecimal("150.00"));
        order.setState(OrderState.PAID);
        return order;
    }

    private CustomerOrder createTestOrderWithState(String orderNumber, OrderState state) {
        Station station = new Station();
        station.setName("Main Bar");

        CustomerOrder order = new CustomerOrder();
        order.setCustomerPhone("+27123456789");
        order.setVisualOrderNumber(orderNumber);
        order.setStation(station);
        order.setTotalPrice(new BigDecimal("100.00"));
        order.setState(state);
        return order;
    }

    private WhatsAppResponse createSuccessResponse(String messageId) {
        WhatsAppResponse.Message message = new WhatsAppResponse.Message(messageId);
        return new WhatsAppResponse("whatsapp", List.of(), List.of(message));
    }
}
