package com.turfzy.common;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Wraps Redisson distributed lock for clean usage across services.
 *
 * Why Redis lock IN ADDITION to DB pessimistic lock?
 * - DB pessimistic lock (SELECT FOR UPDATE) protects within one DB instance
 * - Redis lock protects across multiple application instances (horizontal scaling)
 * - Redis lock is acquired BEFORE the DB transaction starts — cheaper to fail fast
 *   than to start a DB transaction, hold a connection, then discover contention
 *
 * Lock key pattern: "slot_lock:{slotId}"
 * Wait time: 5 seconds max to acquire
 * Lease time: 10 seconds max to hold (auto-released if app crashes)
 */
@Service
public class RedisLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);
    private static final String SLOT_LOCK_PREFIX = "slot_lock:";
    private static final long WAIT_TIME_SECONDS  = 5L;
    private static final long LEASE_TIME_SECONDS = 10L;

    private final RedissonClient redissonClient;

    public RedisLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * Executes the given action while holding an exclusive Redis lock on the slot.
     * If the lock cannot be acquired within WAIT_TIME_SECONDS, throws SlotLockException.
     *
     * Usage:
     *   return redisLockService.executeWithSlotLock(slotId, () -> {
     *       // critical section
     *   });
     */
    public <T> T executeWithSlotLock(Long slotId, Supplier<T> action) {
        String lockKey = SLOT_LOCK_PREFIX + slotId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("Could not acquire Redis lock for slotId={} — slot is being booked by another request", slotId);
                throw new SlotLockException(
                    "This slot is currently being processed by another request. Please try again.");
            }

            log.debug("Redis lock acquired for slotId={}", slotId);
            return action.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SlotLockException("Booking interrupted. Please try again.");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Redis lock released for slotId={}", slotId);
            }
        }
    }

    /** Custom exception for lock acquisition failure */
    public static class SlotLockException extends RuntimeException {
        public SlotLockException(String message) {
            super(message);
        }
    }
}