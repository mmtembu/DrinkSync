package com.smarteventbar.service.impl;

import com.smarteventbar.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory sliding window rate limiter.
 * <p>
 * Maintains a {@link ConcurrentHashMap} of timestamp deques per key for each rate limit type.
 * On check: removes timestamps older than the window, counts remaining.
 * On record: adds the current timestamp to the deque.
 * </p>
 * <p>
 * Rate limits:
 * <ul>
 *   <li>Order creation: 5 requests per 60 seconds per session</li>
 *   <li>Payment attempts: 3 requests per 60 seconds per order</li>
 *   <li>Auth attempts: 5 failed attempts per 900 seconds (15 min) per station</li>
 * </ul>
 * </p>
 * <p>
 * Validates: Requirements 12.3, 12.4, 17.6, 17.7
 * </p>
 */
@Service
public class RateLimitServiceImpl implements RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitServiceImpl.class);

    static final int ORDER_CREATION_LIMIT = 5;
    static final Duration ORDER_CREATION_WINDOW = Duration.ofSeconds(60);

    static final int PAYMENT_ATTEMPT_LIMIT = 3;
    static final Duration PAYMENT_ATTEMPT_WINDOW = Duration.ofSeconds(60);

    static final int AUTH_ATTEMPT_LIMIT = 5;
    static final Duration AUTH_ATTEMPT_WINDOW = Duration.ofSeconds(900);

    private final ConcurrentHashMap<String, Deque<Instant>> orderCreationCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> paymentAttemptCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> authAttemptCounters = new ConcurrentHashMap<>();

    @Override
    public boolean isOrderCreationAllowed(String sessionId) {
        String key = "order:" + sessionId;
        return isAllowed(orderCreationCounters, key, ORDER_CREATION_WINDOW, ORDER_CREATION_LIMIT);
    }

    @Override
    public boolean isPaymentAttemptAllowed(Long orderId) {
        String key = "payment:" + orderId;
        return isAllowed(paymentAttemptCounters, key, PAYMENT_ATTEMPT_WINDOW, PAYMENT_ATTEMPT_LIMIT);
    }

    @Override
    public boolean isAuthAttemptAllowed(Long stationId) {
        String key = "auth:" + stationId;
        return isAllowed(authAttemptCounters, key, AUTH_ATTEMPT_WINDOW, AUTH_ATTEMPT_LIMIT);
    }

    @Override
    public void recordOrderCreation(String sessionId) {
        String key = "order:" + sessionId;
        record(orderCreationCounters, key);
        log.debug("Recorded order creation for session {}", sessionId);
    }

    @Override
    public void recordPaymentAttempt(Long orderId) {
        String key = "payment:" + orderId;
        record(paymentAttemptCounters, key);
        log.debug("Recorded payment attempt for order {}", orderId);
    }

    @Override
    public void recordFailedAuthAttempt(Long stationId) {
        String key = "auth:" + stationId;
        record(authAttemptCounters, key);
        log.debug("Recorded failed auth attempt for station {}", stationId);
    }

    /**
     * Checks whether the current request count within the sliding window is below the limit.
     */
    private boolean isAllowed(ConcurrentHashMap<String, Deque<Instant>> counters,
                              String key, Duration window, int limit) {
        Deque<Instant> timestamps = counters.get(key);
        if (timestamps == null) {
            return true;
        }

        Instant cutoff = Instant.now().minus(window);
        purgeExpired(timestamps, cutoff);

        return timestamps.size() < limit;
    }

    /**
     * Records a new timestamp for the given key.
     */
    private void record(ConcurrentHashMap<String, Deque<Instant>> counters, String key) {
        Deque<Instant> timestamps = counters.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        timestamps.addLast(Instant.now());
    }

    /**
     * Removes all timestamps older than the cutoff from the front of the deque.
     * Since timestamps are added in chronological order, we only need to remove from the head.
     */
    private void purgeExpired(Deque<Instant> timestamps, Instant cutoff) {
        Iterator<Instant> iterator = timestamps.iterator();
        while (iterator.hasNext()) {
            Instant ts = iterator.next();
            if (ts.isBefore(cutoff)) {
                iterator.remove();
            } else {
                // Timestamps are in order, so once we find one that's not expired,
                // all subsequent ones are also not expired
                break;
            }
        }
    }

    /**
     * Calculates the number of seconds until the oldest entry in the window expires.
     * Used to provide a retry-after hint to clients.
     *
     * @param counters the counter map
     * @param key      the rate limit key
     * @param window   the sliding window duration
     * @return seconds until the oldest entry expires, or 1 if the deque is empty
     */
    long getRetryAfterSeconds(ConcurrentHashMap<String, Deque<Instant>> counters,
                              String key, Duration window) {
        Deque<Instant> timestamps = counters.get(key);
        if (timestamps == null || timestamps.isEmpty()) {
            return 1;
        }

        Instant oldest = timestamps.peekFirst();
        if (oldest == null) {
            return 1;
        }

        Instant expiresAt = oldest.plus(window);
        long seconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        return Math.max(1, seconds);
    }

    /**
     * Returns the retry-after seconds for order creation rate limit.
     */
    public long getOrderCreationRetryAfterSeconds(String sessionId) {
        return getRetryAfterSeconds(orderCreationCounters, "order:" + sessionId, ORDER_CREATION_WINDOW);
    }

    /**
     * Returns the retry-after seconds for payment attempt rate limit.
     */
    public long getPaymentRetryAfterSeconds(Long orderId) {
        return getRetryAfterSeconds(paymentAttemptCounters, "payment:" + orderId, PAYMENT_ATTEMPT_WINDOW);
    }

    /**
     * Returns the retry-after seconds for auth attempt rate limit.
     */
    public long getAuthRetryAfterSeconds(Long stationId) {
        return getRetryAfterSeconds(authAttemptCounters, "auth:" + stationId, AUTH_ATTEMPT_WINDOW);
    }
}
