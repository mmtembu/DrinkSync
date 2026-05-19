package com.smarteventbar.service.impl;

import com.smarteventbar.dto.OrderItemRequest;
import com.smarteventbar.model.entity.*;
import com.smarteventbar.model.enums.CupOption;
import com.smarteventbar.model.enums.OrderItemType;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.model.enums.TransitionTrigger;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.*;
import com.smarteventbar.service.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StationRepository stationRepository;
    private final CustomerSessionRepository sessionRepository;
    private final SpiritItemRepository spiritItemRepository;
    private final MixerItemRepository mixerItemRepository;
    private final PremadeItemRepository premadeItemRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderStateMachine stateMachine;
    private final PriceCalculationService priceCalculationService;
    private final QueueService queueService;
    private final VisualOrderNumberService visualOrderNumberService;
    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            StationRepository stationRepository,
                            CustomerSessionRepository sessionRepository,
                            SpiritItemRepository spiritItemRepository,
                            MixerItemRepository mixerItemRepository,
                            PremadeItemRepository premadeItemRepository,
                            IdempotencyKeyRepository idempotencyKeyRepository,
                            OrderStateMachine stateMachine,
                            PriceCalculationService priceCalculationService,
                            QueueService queueService,
                            VisualOrderNumberService visualOrderNumberService,
                            SessionService sessionService,
                            SimpMessagingTemplate messagingTemplate,
                            NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.stationRepository = stationRepository;
        this.sessionRepository = sessionRepository;
        this.spiritItemRepository = spiritItemRepository;
        this.mixerItemRepository = mixerItemRepository;
        this.premadeItemRepository = premadeItemRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.stateMachine = stateMachine;
        this.priceCalculationService = priceCalculationService;
        this.queueService = queueService;
        this.visualOrderNumberService = visualOrderNumberService;
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public CustomerOrder createOrder(Long stationId, String sessionId, List<OrderItemRequest> items) {
        sessionService.validateSessionForStation(sessionId, stationId);

        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new EntityNotFoundException("Station not found: " + stationId));
        CustomerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + sessionId));

        CustomerOrder order = new CustomerOrder(station, session);
        order = orderRepository.save(order);

        addOrderItems(order, items, station);

        BigDecimal total = priceCalculationService.calculateOrderTotal(order.getOrderItems());
        order.setTotalPrice(total);

        recordStateHistory(order, null, OrderState.DRAFT, TransitionTrigger.CUSTOMER);
        sessionService.touchSession(sessionId);

        order = orderRepository.save(order);

        MDC.put("orderId", order.getId().toString());
        MDC.put("stationId", stationId.toString());
        log.info("Order created: orderId={} stationId={} itemCount={} totalPrice={}",
                order.getId(), stationId, items.size(), total);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrder getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
    }

    @Override
    @Transactional
    public CustomerOrder updateOrderItems(Long orderId, String sessionId, List<OrderItemRequest> items) {
        CustomerOrder order = getOrderForSession(orderId, sessionId);

        if (order.getState() != OrderState.DRAFT) {
            throw new IllegalStateException("Can only update items for DRAFT orders");
        }

        order.getOrderItems().clear();
        orderRepository.flush();

        addOrderItems(order, items, order.getStation());

        BigDecimal total = priceCalculationService.calculateOrderTotal(order.getOrderItems());
        order.setTotalPrice(total);
        order.setUpdatedAt(LocalDateTime.now());

        sessionService.touchSession(sessionId);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public CustomerOrder checkout(Long orderId, String sessionId) {
        return checkout(orderId, sessionId, null, null);
    }

    @Override
    @Transactional
    public CustomerOrder checkout(Long orderId, String sessionId, String customerPhone, Boolean whatsappOptIn) {
        CustomerOrder order = getOrderForSession(orderId, sessionId);

        stateMachine.validateTransition(order.getState(), OrderState.AWAITING_PAYMENT);

        // Resolve phone and opt-in: use provided values, or pre-fill from session
        CustomerSession session = order.getSession();
        String resolvedPhone = customerPhone;
        boolean resolvedOptIn = whatsappOptIn != null ? whatsappOptIn : false;

        if ((resolvedPhone == null || resolvedPhone.isBlank()) && session != null) {
            // Pre-fill from session values
            resolvedPhone = session.getCustomerPhone();
            if (whatsappOptIn == null && session.isWhatsappOptIn()) {
                resolvedOptIn = true;
            }
        }

        // Store phone and opt-in on the Order entity
        if (resolvedPhone != null && !resolvedPhone.isBlank()) {
            order.setCustomerPhone(resolvedPhone);
        }
        order.setWhatsappOptIn(resolvedOptIn);

        // Store phone and opt-in on the Session entity (update with latest values)
        if (session != null) {
            if (resolvedPhone != null && !resolvedPhone.isBlank()) {
                session.setCustomerPhone(resolvedPhone);
            }
            session.setWhatsappOptIn(resolvedOptIn);
            sessionRepository.save(session);
        }

        OrderState previousState = order.getState();
        order.setState(OrderState.AWAITING_PAYMENT);
        order.setUpdatedAt(LocalDateTime.now());

        recordStateHistory(order, previousState, OrderState.AWAITING_PAYMENT, TransitionTrigger.CUSTOMER);
        sessionService.touchSession(sessionId);

        order = orderRepository.save(order);
        broadcastOrderUpdate(order);

        MDC.put("orderId", orderId.toString());
        log.info("Order state transition: orderId={} fromState={} toState={} trigger=CUSTOMER",
                orderId, previousState, OrderState.AWAITING_PAYMENT);
        return order;
    }

    @Override
    @Transactional
    public CustomerOrder confirmPayment(Long orderId, String sessionId, UUID idempotencyKey) {
        // Check idempotency key first
        Optional<IdempotencyKey> existingKey = idempotencyKeyRepository
                .findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);

        if (existingKey.isPresent()) {
            IdempotencyKey key = existingKey.get();
            if (key.getExpiresAt().isAfter(LocalDateTime.now())) {
                log.info("Idempotency key {} already processed for order {}", idempotencyKey, orderId);
                return getOrder(orderId);
            }
            // Key expired — allow reprocessing with a new key
        }

        CustomerOrder order = getOrderForSession(orderId, sessionId);
        stateMachine.validateTransition(order.getState(), OrderState.PAID);

        OrderState previousState = order.getState();
        order.setState(OrderState.PAID);
        order.setUpdatedAt(LocalDateTime.now());

        int queuePosition = queueService.assignQueuePosition(order);
        visualOrderNumberService.generateVisualOrderNumber(order);

        recordStateHistory(order, previousState, OrderState.PAID, TransitionTrigger.CUSTOMER);

        // Persist idempotency key
        IdempotencyKey newKey = new IdempotencyKey(order, idempotencyKey, null, 200);
        idempotencyKeyRepository.save(newKey);

        sessionService.touchSession(sessionId);
        order = orderRepository.save(order);
        broadcastOrderUpdate(order);

        // Dispatch notification after transaction commits (outside transaction boundary)
        final CustomerOrder confirmedOrder = order;
        registerAfterCommitNotification(() -> notificationService.notifyOrderConfirmed(confirmedOrder));

        MDC.put("orderId", orderId.toString());
        log.info("Payment confirmed: orderId={} queuePosition={} visualNumber={}",
                orderId, queuePosition, order.getVisualOrderNumber());
        return order;
    }

    @Override
    @Transactional
    public CustomerOrder cancelOrder(Long orderId, String sessionId) {
        CustomerOrder order = getOrderForSession(orderId, sessionId);

        stateMachine.validateTransition(order.getState(), OrderState.CANCELLED);

        OrderState previousState = order.getState();
        order.setState(OrderState.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());

        recordStateHistory(order, previousState, OrderState.CANCELLED, TransitionTrigger.CUSTOMER);
        sessionService.touchSession(sessionId);

        order = orderRepository.save(order);
        broadcastOrderUpdate(order);

        MDC.put("orderId", orderId.toString());
        log.info("Order cancelled: orderId={} previousState={}", orderId, previousState);
        return order;
    }

    @Override
    @Transactional
    public CustomerOrder transitionState(Long orderId, OrderState targetState) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        stateMachine.validateTransition(order.getState(), targetState);

        OrderState previousState = order.getState();
        order.setState(targetState);
        order.setUpdatedAt(LocalDateTime.now());

        if (targetState == OrderState.READY) {
            order.setPickupWindowStart(LocalDateTime.now());
        }

        recordStateHistory(order, previousState, targetState, TransitionTrigger.VENDOR);

        order = orderRepository.save(order);
        broadcastOrderUpdate(order);

        // Dispatch notification after transaction commits (outside transaction boundary)
        final CustomerOrder transitionedOrder = order;
        if (targetState == OrderState.READY) {
            registerAfterCommitNotification(() -> notificationService.notifyOrderReady(transitionedOrder));
        } else if (targetState == OrderState.COLLECTED) {
            registerAfterCommitNotification(() -> notificationService.notifyOrderCollected(transitionedOrder));
        }

        MDC.put("orderId", orderId.toString());
        log.info("Order state transition: orderId={} fromState={} toState={} trigger=VENDOR",
                orderId, previousState, targetState);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerOrder> getOrdersByStationAndState(Long stationId, OrderState state) {
        return orderRepository.findByStationIdAndState(stationId, state);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerOrder> getActiveOrdersByStation(Long stationId) {
        Set<OrderState> activeStates = EnumSet.of(
                OrderState.PAID, OrderState.PREPARING, OrderState.READY, OrderState.COLLECTED);
        return orderRepository.findByStationIdAndStateIn(stationId, activeStates);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerOrder> getOrdersBySession(String sessionId) {
        return orderRepository.findBySessionSessionId(sessionId);
    }

    @Override
    @Transactional
    public void expireOrder(Long orderId) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        stateMachine.validateTransition(order.getState(), OrderState.EXPIRED);

        OrderState previousState = order.getState();
        order.setState(OrderState.EXPIRED);
        order.setUpdatedAt(LocalDateTime.now());

        recordStateHistory(order, previousState, OrderState.EXPIRED, TransitionTrigger.SYSTEM);

        orderRepository.save(order);
        broadcastOrderUpdate(order);

        MDC.put("orderId", orderId.toString());
        log.info("Order state transition: orderId={} fromState={} toState=EXPIRED trigger=SYSTEM readySince={}",
                orderId, previousState, order.getPickupWindowStart());
    }

    private CustomerOrder getOrderForSession(Long orderId, String sessionId) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (order.getSession() == null || !order.getSession().getSessionId().equals(sessionId)) {
            throw new IllegalArgumentException("Order does not belong to this session");
        }
        return order;
    }

    private void addOrderItems(CustomerOrder order, List<OrderItemRequest> items, Station station) {
        for (OrderItemRequest req : items) {
            OrderItem item;
            if (req.getItemType() == OrderItemType.CUSTOM_DRINK) {
                // Validate at least one spirit and one mixer
                if (req.getSpiritItemIds() == null || req.getSpiritItemIds().isEmpty()) {
                    throw new IllegalArgumentException("Custom drink requires at least one spirit");
                }
                if (req.getMixerItemIds() == null || req.getMixerItemIds().isEmpty()) {
                    throw new IllegalArgumentException("Custom drink requires at least one mixer");
                }

                // Load and validate all selected spirits
                List<BigDecimal> spiritPrices = new java.util.ArrayList<>();
                List<SpiritItem> spirits = new java.util.ArrayList<>();
                for (Long spiritId : req.getSpiritItemIds()) {
                    SpiritItem spirit = spiritItemRepository.findById(spiritId)
                            .orElseThrow(() -> new EntityNotFoundException("Spirit item not found: " + spiritId));
                    if (!spirit.isAvailable()) {
                        throw new IllegalArgumentException("Spirit item '" + spirit.getName() + "' is not available");
                    }
                    spirits.add(spirit);
                    spiritPrices.add(spirit.getPrice());
                }

                // Load and validate all selected mixers
                List<BigDecimal> mixerPrices = new java.util.ArrayList<>();
                List<MixerItem> mixers = new java.util.ArrayList<>();
                for (Long mixerId : req.getMixerItemIds()) {
                    MixerItem mixer = mixerItemRepository.findById(mixerId)
                            .orElseThrow(() -> new EntityNotFoundException("Mixer item not found: " + mixerId));
                    if (!mixer.isAvailable()) {
                        throw new IllegalArgumentException("Mixer item '" + mixer.getName() + "' is not available");
                    }
                    mixers.add(mixer);
                    mixerPrices.add(mixer.getPrice());
                }

                CupOption cupOption = req.getCupOption();
                BigDecimal unitPrice = priceCalculationService.calculateCustomDrinkPrice(
                        spiritPrices, mixerPrices, cupOption, station.getCupPrice());

                item = new OrderItem(order, OrderItemType.CUSTOM_DRINK, req.getQuantity(), unitPrice);
                item.setCupOption(cupOption);
                item.setCupPrice(cupOption == CupOption.NEW_CUP ? station.getCupPrice() : BigDecimal.ZERO);
                item.setSpiritItems(spirits);
                item.setMixerItems(mixers);
            } else {
                PremadeItem premade = premadeItemRepository.findById(req.getPremadeItemId())
                        .orElseThrow(() -> new EntityNotFoundException("Premade item not found"));

                if (!premade.isAvailable()) {
                    throw new IllegalArgumentException("Premade item '" + premade.getName() + "' is not available");
                }

                item = new OrderItem(order, OrderItemType.PREMADE, req.getQuantity(), premade.getPrice());
                item.setPremadeItem(premade);
            }
            order.getOrderItems().add(item);
        }
    }

    private void recordStateHistory(CustomerOrder order, OrderState from, OrderState to, TransitionTrigger trigger) {
        OrderStateHistory history = new OrderStateHistory(order, from, to, trigger);
        order.getStateHistory().add(history);
    }

    private void broadcastOrderUpdate(CustomerOrder order) {
        try {
            com.smarteventbar.dto.OrderResponse response = com.smarteventbar.dto.OrderResponse.fromEntity(order);
            messagingTemplate.convertAndSend(
                    "/topic/stations/" + order.getStation().getId() + "/orders", response);
            messagingTemplate.convertAndSend(
                    "/topic/orders/" + order.getId(), response);
        } catch (Exception e) {
            log.error("Failed to broadcast order update for order {}", order.getId(), e);
        }
    }

    /**
     * Registers a callback to be executed after the current transaction commits successfully.
     * This ensures notification dispatch happens outside the transaction boundary,
     * so notification failures cannot cause a transaction rollback, and notifications
     * are not sent if the transaction rolls back.
     */
    private void registerAfterCommitNotification(Runnable notificationAction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        notificationAction.run();
                    } catch (Exception e) {
                        log.error("Failed to dispatch notification after commit: {}", e.getMessage(), e);
                    }
                }
            });
        } else {
            // No active transaction synchronization — execute directly (e.g., in tests)
            try {
                notificationAction.run();
            } catch (Exception e) {
                log.error("Failed to dispatch notification (no active transaction): {}", e.getMessage(), e);
            }
        }
    }
}
