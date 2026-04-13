package nidam.tokengenerator.service;

/**
 * Interface for tracking login failures and enforcing temporary lockouts.
 * <p>
 * Implementations may record failed login attempts, determine lockout status,
 * reset failure counts, and periodically clean up expired lockouts.
 * <p>
 * This abstraction allows for conditional activation via configuration,
 * enabling or disabling rate-limiting behavior without affecting authentication flow.
 */
public interface LoginFailureAuditor {

	/**
	 * Records a failed login attempt for the given key (e.g. username + IP).
	 *
	 * @param key a unique identifier for the login source
	 */
	void recordFailure(String key);

	/**
	 * Checks whether the given key is currently locked out due to excessive failures.
	 *
	 * @param key the identifier to check
	 * @return {@code true} if locked out, {@code false} otherwise
	 */
	boolean isLockedOut(String key);

	/**
	 * Clears any recorded failures for the given key.
	 *
	 * @param key the identifier to reset
	 */
	void resetFailures(String key);

	/**
	 * Returns the number of recorded failures for the given key.
	 *
	 * @param key the identifier to query
	 * @return the failure count, or {@code 0} if none
	 */
	int getFailureCount(String key);

	/**
	 * Removes expired lockout records based on configured thresholds.
	 * Typically invoked by a scheduled cleanup task.
	 */
	void cleanupExpiredLockouts();
}
