package nidam.tokengenerator.service;

import nidam.tokengenerator.service.properties.AuditorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Real implementation of {@link LoginFailureAuditor} that tracks login failures and enforces temporary lockouts.
 * <p>
 * Activated when the {@code rate-limiter.enabled} property is set to {@code true}.
 * Maintains an in-memory map of failure records keyed by user/IP combinations,
 * and applies lockout logic based on configurable thresholds.
 * <p>
 * Lockouts are time-bound and automatically expire after a defined duration.
 * This implementation is suitable for production environments requiring brute-force protection.
 */
@Component
@ConditionalOnProperty(name = "rate-limiter.enabled", havingValue = "true")
public class RealLoginFailureAuditor implements LoginFailureAuditor {

	/**
	 * Represents a login failure record for a specific user/IP key.
	 * <p>
	 * Tracks the number of failed attempts and the timestamp of the most recent failure.
	 * Provides logic to determine whether the key is currently locked out or eligible for cleanup.
	 */
	private static class FailureRecord {
		AtomicInteger count = new AtomicInteger();
		Instant lastFailure = Instant.now();

		/**
		 * Records a new failure by incrementing the count and updating the timestamp.
		 */
		void record() {
			count.incrementAndGet();
			lastFailure = Instant.now();
		}

		/**
		 * Determines whether the record is currently locked out.
		 *
		 * @param maxAttempts the maximum allowed failures before lockout
		 * @param lockoutDuration the duration of the lockout window
		 * @return {@code true} if locked out, {@code false} otherwise
		 */
		boolean isLockedOut(int maxAttempts, Duration lockoutDuration) {
			return count.get() > maxAttempts && Duration.between(lastFailure, Instant.now()).compareTo(lockoutDuration) < 0;
		}

		/**
		 * Determines whether the lockout has expired and the record can be cleaned up.
		 *
		 * @param now the current timestamp
		 * @param maxAttempts the maximum allowed failures before lockout
		 * @param lockoutDuration the duration of the lockout window
		 * @return {@code true} if expired, {@code false} otherwise
		 */
		boolean isExpired(Instant now, int maxAttempts, Duration lockoutDuration) {
			return count.get() > maxAttempts && Duration.between(lastFailure, now).compareTo(lockoutDuration) >= 0;
		}
	}

	private final Logger log = Logger.getLogger(RealLoginFailureAuditor.class.getName());
	private final Map<String, FailureRecord> attempts = new ConcurrentHashMap<>();
	private final Duration LOCKOUT_DURATION;
	private final int MAX_ATTEMPTS;

	/**
	 * Constructs a {@code RealLoginFailureAuditor} with configuration-driven thresholds.
	 * <p>
	 * Initializes the lockout duration and maximum allowed attempts using values
	 * provided by {@link AuditorProperties}, ensuring that rate-limiting behavior
	 * is externally configurable.
	 *
	 * @param rateLimiter the configuration properties for rate-limiting thresholds
	 */
	public RealLoginFailureAuditor(AuditorProperties rateLimiter) {
		LOCKOUT_DURATION = Duration.ofMinutes(rateLimiter.getLockoutDuration());
		MAX_ATTEMPTS = rateLimiter.getMaxAttempts();
	}

	/**
	 * Records a failed login attempt for the specified key.
	 * <p>
	 * If no prior record exists for the key, a new {@code FailureRecord} is created.
	 * The failure count is incremented and the timestamp is updated.
	 *
	 * @param key a unique identifier for the login source (e.g. username + IP)
	 */
	@Override
	public void recordFailure(String key) {
		log.info("record login failure: " + key);
		FailureRecord failureRecord = attempts.computeIfAbsent(key, k -> new FailureRecord());
		failureRecord.record();
	}

	/**
	 * Determines whether the specified key is currently locked out.
	 * <p>
	 * A key is considered locked out if the number of recorded failures exceeds
	 * the configured threshold and the most recent failure occurred within the lockout window.
	 *
	 * @param key the identifier to check
	 * @return {@code true} if locked out, {@code false} otherwise
	 */
	@Override
	public boolean isLockedOut(String key) {
		FailureRecord fr = attempts.get(key);
		if (fr == null) return false;

		boolean locked = fr.isLockedOut(MAX_ATTEMPTS, LOCKOUT_DURATION);
		log.info("is locked out: " + key + " - " + locked);

		return locked;
	}

	/**
	 * Clears any recorded login failures for the specified key.
	 * <p>
	 * This is typically invoked after a successful login to reset the lockout state.
	 *
	 * @param key the identifier to reset
	 */
	@Override
	public void resetFailures(String key) {
//		log.info("reset login failures: " + key);
		attempts.remove(key);
	}

	/**
	 * Retrieves the number of recorded login failures for the specified key.
	 *
	 * @param key the identifier to query
	 * @return the failure count, or {@code 0} if no record exists
	 */
	@Override
	public int getFailureCount(String key) {
		Integer failureAttempts = Optional.ofNullable(attempts.get(key)).map(fr -> fr.count.get()).orElse(0);
		log.info("login failure attempts: " + key + " - " + failureAttempts);
		return failureAttempts;
	}

	/**
	 * Removes expired lockout records from the internal tracking map.
	 * <p>
	 * A record is considered expired if its failure count exceeds the threshold
	 * and its last failure occurred outside the configured lockout duration.
	 * <p>
	 * This method is typically invoked by a scheduled cleanup task.
	 */
	@Override
	public void cleanupExpiredLockouts() {
		int initial = attempts.size();
		if( initial > 0){
			Instant now = Instant.now();
			attempts.entrySet().removeIf(entry -> {
				FailureRecord fr = entry.getValue();
				return fr.isExpired(now, MAX_ATTEMPTS, LOCKOUT_DURATION);
			});
			int finalSize = attempts.size();
			log.info("cleanup expired lockouts: " + initial + " -> " + finalSize);
		}
	}
}
