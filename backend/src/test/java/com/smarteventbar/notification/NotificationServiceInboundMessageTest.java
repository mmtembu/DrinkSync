package com.smarteventbar.notification;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService - processInboundMessage")
class NotificationServiceInboundMessageTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private NotificationChannel whatsAppChannel;

    @Mock
    private OrderRepository orderRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationLogRepository, Optional.of(whatsAppChannel), orderRepository);
    }

    @Test
    @DisplayName("Should send support reply with active orders when orders found")
    void shouldSendSupportReplyWhenOrdersFound() {
        String phone = "+27821234567";
        List<CustomerOrder> activeOrders = createOrders(3);
        when(notificationLogRepository.findActiveOrdersByPhone(phone)).thenReturn(activeOrders);
        when(whatsAppChannel.isEnabled()).thenReturn(true);
        when(whatsAppChannel.sendSupportReply(anyString(), anyList())).thenReturn("wamid.123");

        notificationService.processInboundMessage(phone, "Where is my order?");

        verify(whatsAppChannel).sendSupportReply(eq(phone), eq(activeOrders));
    }

    @Test
    @DisplayName("Should not send reply when no active orders found")
    void shouldNotSendReplyWhenNoOrdersFound() {
        String phone = "+27821234567";
        when(notificationLogRepository.findActiveOrdersByPhone(phone)).thenReturn(Collections.emptyList());

        notificationService.processInboundMessage(phone, "Where is my order?");

        verify(whatsAppChannel, never()).sendSupportReply(anyString(), anyList());
    }

    @Test
    @DisplayName("Should limit reply to 5 most recent orders when more than 5 active")
    @SuppressWarnings("unchecked")
    void shouldLimitToFiveMostRecentOrders() {
        String phone = "+27821234567";
        List<CustomerOrder> activeOrders = createOrders(8);
        when(notificationLogRepository.findActiveOrdersByPhone(phone)).thenReturn(activeOrders);
        when(whatsAppChannel.isEnabled()).thenReturn(true);
        when(whatsAppChannel.sendSupportReply(anyString(), anyList())).thenReturn("wamid.123");

        notificationService.processInboundMessage(phone, "Status?");

        ArgumentCaptor<List<CustomerOrder>> ordersCaptor = ArgumentCaptor.forClass(List.class);
        verify(whatsAppChannel).sendSupportReply(eq(phone), ordersCaptor.capture());

        List<CustomerOrder> sentOrders = ordersCaptor.getValue();
        assertThat(sentOrders).hasSize(5);
        // Should be the first 5 from the list (most recent, since repo returns DESC)
        assertThat(sentOrders).isEqualTo(activeOrders.subList(0, 5));
    }

    @Test
    @DisplayName("Should send all orders when exactly 5 active orders")
    @SuppressWarnings("unchecked")
    void shouldSendAllOrdersWhenExactlyFive() {
        String phone = "+27821234567";
        List<CustomerOrder> activeOrders = createOrders(5);
        when(notificationLogRepository.findActiveOrdersByPhone(phone)).thenReturn(activeOrders);
        when(whatsAppChannel.isEnabled()).thenReturn(true);
        when(whatsAppChannel.sendSupportReply(anyString(), anyList())).thenReturn("wamid.123");

        notificationService.processInboundMessage(phone, "Status?");

        ArgumentCaptor<List<CustomerOrder>> ordersCaptor = ArgumentCaptor.forClass(List.class);
        verify(whatsAppChannel).sendSupportReply(eq(phone), ordersCaptor.capture());

        assertThat(ordersCaptor.getValue()).hasSize(5);
    }

    @Test
    @DisplayName("Should not propagate exceptions from channel")
    void shouldNotPropagateExceptionsFromChannel() {
        String phone = "+27821234567";
        List<CustomerOrder> activeOrders = createOrders(2);
        when(notificationLogRepository.findActiveOrdersByPhone(phone)).thenReturn(activeOrders);
        when(whatsAppChannel.isEnabled()).thenReturn(true);
        when(whatsAppChannel.sendSupportReply(anyString(), anyList()))
                .thenThrow(new RuntimeException("WhatsApp API error"));

        // Should not throw
        notificationService.processInboundMessage(phone, "Status?");

        verify(whatsAppChannel).sendSupportReply(eq(phone), eq(activeOrders));
    }

    @Test
    @DisplayName("Should handle single active order")
    void shouldHandleSingleActiveOrder() {
        String phone = "+27821234567";
        List<CustomerOrder> activeOrders = createOrders(1);
        when(notificationLogRepository.findActiveOrdersByPhone(phone)).thenReturn(activeOrders);
        when(whatsAppChannel.isEnabled()).thenReturn(true);
        when(whatsAppChannel.sendSupportReply(anyString(), anyList())).thenReturn("wamid.123");

        notificationService.processInboundMessage(phone, "Hi");

        verify(whatsAppChannel).sendSupportReply(eq(phone), eq(activeOrders));
    }

    @Test
    @DisplayName("Should not send reply when channel is disabled")
    void shouldNotSendReplyWhenChannelDisabled() {
        String phone = "+27821234567";
        List<CustomerOrder> activeOrders = createOrders(2);
        when(notificationLogRepository.findActiveOrdersByPhone(phone)).thenReturn(activeOrders);
        when(whatsAppChannel.isEnabled()).thenReturn(false);

        notificationService.processInboundMessage(phone, "Status?");

        verify(whatsAppChannel, never()).sendSupportReply(anyString(), anyList());
    }

    private List<CustomerOrder> createOrders(int count) {
        List<CustomerOrder> orders = new ArrayList<>();
        OrderState[] activeStates = {OrderState.PAID, OrderState.PREPARING, OrderState.READY};

        for (int i = 0; i < count; i++) {
            CustomerOrder order = new CustomerOrder();
            order.setId((long) (i + 1));
            order.setVisualOrderNumber("ORD-" + (i + 1));
            order.setState(activeStates[i % activeStates.length]);
            order.setTotalPrice(BigDecimal.valueOf(50 + i * 10));
            order.setCreatedAt(LocalDateTime.now().minusMinutes(i));
            order.setUpdatedAt(LocalDateTime.now().minusMinutes(i));
            order.setCustomerPhone("+27821234567");
            order.setWhatsappOptIn(true);
            orders.add(order);
        }
        return orders;
    }
}
