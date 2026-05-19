package com.smarteventbar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A bounded send queue with token-bucket rate limiting for outbound WhatsApp messages.
 * Ensures the system stays within Meta's API rate limits (default 50 msg/s).
 *
 * The token bucket refills at a configurable rate per second. Each message send
 * consumes one token. If no tokens are available, the caller blocks briefly until
 * a token becomes available.
 *
 * When a rate limit response (HTTP 429) is received from Meta's API, the queue
 * can be paused for the duration specified in the Retry-After header via
 * {@link #pauseForDuration(Duration)}.
 */
public class RateLimitedSendQueue {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedSendQueue.class);

    private final BlockingQueue<Runnable> queue;
    private final int maxRatePerSecond;
    private final long tokenRefillIntervalNanos;
    private final AtomicLong nextAllowedSendTimeNanos;

    /**
     * Tracks the nanoTime until which the queue is paused due to a rate limit response.
     * When pausedUntilNanos > System.nanoTime(), the queue will wait before processing.
     */
    private volatile long pausedUntilNanos = 0;

    /**
     * Creates a rate-limited send queue.
     *
     * @param capacity         maximum number of messages that can be queued
     * @param maxRatePerSecond maximum send rate in messages per second (token bucket refill rate)
     */
    public RateLimitedSendQueue(int capacity, int maxRatePerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        if (maxRatePerSecond <= 0) {
            throw new IllegalArgumentException("Max rate per second must be positive");
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.maxRatePerSecond = maxRatePerSecond;
        this.tokenRefillIntervalNanos = 1_000_000_000L / maxRatePerSecond;
        this.nextAllowedSendTimeNanos = new AtomicLong(System.nanoTime());
    }

    /**
     * Enqueue a send task. Returns true if the task was accepted, false if the queue is full.
     *
     * @param sendTask the task to execute (typically sends a WhatsApp message)
     * @return true if enqueued successfully, false if queue is at capacity
     */
    public boolean enqueue(Runnable sendTask) {
        boolean offered = queue.offer(sendTask);
        if (!offered) {
            log.warn("Outbound message queue is full (capacity: {}). Message will be marked as 'queued'.",
                    queue.size() + queue.remainingCapacity());
            return false;
        }
        // Process the task respecting rate limits
        processNext();
        return true;
    }

    /**
     * Acquires a token from the token bucket, blocking if necessary until the rate limit allows.
     * Also respects any active pause duration from a Retry-After header.
     * Uses a compare-and-swap loop for thread safety.
     */
    public void acquireToken() {
        // First, respect any active pause from a rate limit response
        waitForPause();

        while (true) {
            long now = System.nanoTime();
            long currentNext = nextAllowedSendTimeNanos.get();
            long waitTime = currentNext - now;

            // Calculate the new next allowed time
            long newNext = Math.max(now, currentNext) + tokenRefillIntervalNanos;

            if (nextAllowedSendTimeNanos.compareAndSet(currentNext, newNext)) {
                // Successfully reserved a slot; wait if needed
                if (waitTime > 0) {
                    try {
                        long waitMillis = waitTime / 1_000_000;
                        int waitNanos = (int) (waitTime % 1_000_000);
                        Thread.sleep(waitMillis, waitNanos);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Rate limiter wait interrupted");
                    }
                }
                return;
            }
            // CAS failed, retry
        }
    }

    /**
     * Pauses queue processing for the specified duration. This is called when the
     * WhatsApp Cloud API returns an HTTP 429 response with a Retry-After header.
     * All subsequent token acquisitions will wait until the pause duration expires.
     *
     * @param retryAfter the duration to pause, as specified by the Retry-After header
     */
    public void pauseForDuration(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()) {
            log.debug("Ignoring invalid retry-after duration: {}", retryAfter);
            return;
        }

        long pauseUntil = System.nanoTime() + retryAfter.toNanos();
        // Only extend the pause, never shorten it
        if (pauseUntil > pausedUntilNanos) {
            pausedUntilNanos = pauseUntil;
        }

        log.warn("Rate limit response received. Pausing outbound message queue for {} seconds.",
                retryAfter.getSeconds());
    }

    /**
     * Returns whether the queue is currently paused due to a rate limit response.
     */
    public boolean isPaused() {
        return System.nanoTime() < pausedUntilNanos;
    }

    /**
     * Returns the current number of tasks waiting in the queue.
     */
    public int getQueueDepth() {
        return queue.size();
    }

    /**
     * Returns the maximum send rate per second.
     */
    public int getMaxRatePerSecond() {
        return maxRatePerSecond;
    }

    /**
     * Returns the total queue capacity.
     */
    public int getCapacity() {
        return queue.size() + queue.remainingCapacity();
    }

    /**
     * Waits if the queue is currently paused due to a Retry-After response.
     */
    private void waitForPause() {
        long pauseEnd = pausedUntilNanos;
        long now = System.nanoTime();
        long remainingWait = pauseEnd - now;

        if (remainingWait > 0) {
            log.debug("Queue is paused due to rate limit. Waiting {} ms before sending.",
                    remainingWait / 1_000_000);
            try {
                long waitMillis = remainingWait / 1_000_000;
                int waitNanos = (int) (remainingWait % 1_000_000);
                Thread.sleep(waitMillis, waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limiter pause wait interrupted");
            }
        }
    }

    private void processNext() {
        Runnable task = queue.poll();
        if (task != null) {
            acquireToken();
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error executing queued send task", e);
            }
        }
    }
}
