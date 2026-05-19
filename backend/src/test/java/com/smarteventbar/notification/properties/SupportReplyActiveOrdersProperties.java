package com.smarteventbar.notification.properties;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.notification.NotificationChannel;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for support reply with active orders.
 *
 * Property 14: Support reply contains active orders
 * For any phone number associated with N active orders (state in PAID, PREPARING, READY),
 * the support reply message SHALL contain min(N, 5) orders. If N is 0, no reply is sent.
 * If N > 5, only the first 5 are included.
 *
 * Validates: Requirements 16.1, 16.2, 16.3, 16.4
 */
class SupportReplyActiveOrdersProperties {

    private static final OrderState[] ACTIVE_STATES = {OrderState.PAID, OrderState.PREPARING, OrderState.READY};

    @Provide
    Arbitrary<String> phoneNumbers() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(9)
                .ofMaxLength(12)
                .map(digits -> "+27" + digits.substring(0, Math.min(digits.length(), 9)));
    }

    @Provide
    Arbitrary<Integer> orderCounts() {
        return Arbitraries.integers().between(0, 10);
    }

    @Provide
    Arbitrary<OrderState> activeStates() {
        return Arbitraries.of(ACTIVE_STATES);
    }

    /**
     * Property 14a: When 0 active orders exist for a phone number, the channel is NOT called.
     */
    @Property(tries = 100)
    void noActiveOrdersMeansNoReply(@ForAll("phoneNumbers") String phone) {

        NotificationLogRepository logRepository = mock(NotificationLogRepository.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        OrderRepository orderRepository = mock(OrderRepository.class);

        when(channel.isEnabled()).thenReturn(true);

        // No active orders for this phone
        when(logRepository.findActiveOrdersByPhone(phone)).thenReturn(List.of());

        NotificationService service = new NotificationService(
                logRepository, Optional.of(channel), orderRepository);

        service.processInboundMessage(phone, "What's my order status?");

        // Channel should NOT be called
        verify(channel, never()).sendSupportReply(anyString(), anyList());
    }

    /**
     * Property 14b: When 1-5 active orders exist, the channel is called with ALL orders.
     */
    @Property(tries = 100)
    void oneToFiveOrdersAllIncluded(
            @ForAll("phoneNumbers") String phone,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 5) int orderCount) {

        NotificationLogRepository logRepository = mock(NotificationLogRepository.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        OrderRepository orderRepository = mock(OrderRepository.class);

        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendSupportReply(anyString(), anyList())).thenReturn("wamid.support123");

        // Generate active orders
        List<CustomerOrder> activeOrders = generateActiveOrders(orderCount, phone);
        when(logRepository.findActiveOrdersByPhone(phone)).thenReturn(activeOrders);

        NotificationService service = new NotificationService(
                logRepository, Optional.of(channel), orderRepository);

        service.processInboundMessage(phone, "What's my order status?");

        // Channel should be called with all orders
        ArgumentCaptor<List<CustomerOrder>> ordersCaptor = ArgumentCaptor.forClass(List.class);
        verify(channel, times(1)).sendSupportReply(eq(phone), ordersCaptor.capture());

        List<CustomerOrder> sentOrders = ordersCaptor.getValue();
        assert sentOrders.size() == orderCount :
                "Expected " + orderCount + " orders in reply but got " + sentOrders.size();
    }

    /**
     * Property 14c: When more than 5 active orders exist, the channel is called with only the first 5.
     */
    @Property(tries = 100)
    void moreThanFiveOrdersLimitedToFive(
            @ForAll("phoneNumbers") String phone,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 6, max = 10) int orderCount) {

        NotificationLogRepository logRepository = mock(NotificationLogRepository.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        OrderRepository orderRepository = mock(OrderRepository.class);

        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendSupportReply(anyString(), anyList())).thenReturn("wamid.support456");

        // Generate active orders
        List<CustomerOrder> activeOrders = generateActiveOrders(orderCount, phone);
        when(logRepository.findActiveOrdersByPhone(phone)).thenReturn(activeOrders);

        NotificationService service = new NotificationService(
                logRepository, Optional.of(channel), orderRepository);

        service.processInboundMessage(phone, "What's my order status?");

        // Channel should be called with only 5 orders
        ArgumentCaptor<List<CustomerOrder>> ordersCaptor = ArgumentCaptor.forClass(List.class);
        verify(channel, times(1)).sendSupportReply(eq(phone), ordersCaptor.capture());

        List<CustomerOrder> sentOrders = ordersCaptor.getValue();
        assert sentOrders.size() == 5 :
                "Expected 5 orders in reply (capped) but got " + sentOrders.size();
    }

    // --- Helper methods ---

    private List<CustomerOrder> generateActiveOrders(int count, String phone) {
        List<CustomerOrder> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CustomerOrder order = new CustomerOrder();
            order.setId((long) (i + 1));
            order.setCustomerPhone(phone);
            order.setVisualOrderNumber("ORD-" + (i + 1));
            order.setState(ACTIVE_STATES[i % ACTIVE_STATES.length]);
            order.setCreatedAt(LocalDateTime.now().minusMinutes(i));
            orders.add(order);
        }
        return orders;
    }
}
