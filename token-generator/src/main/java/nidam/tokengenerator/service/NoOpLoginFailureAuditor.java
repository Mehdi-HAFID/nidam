package nidam.tokengenerator.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * No-op implementation of {@link LoginFailureAuditor} used when rate limiting is disabled.
 * <p>
 * Activated when the {@code rate-limiter.enabled} property is set to {@code false}
 * or is missing entirely. This implementation performs no tracking or enforcement,
 * allowing all login attempts to proceed without restriction.
 * <p>
 * Useful for environments where lockout policies are not required or during development/testing.
 */
@Component
@ConditionalOnProperty(name = "rate-limiter.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpLoginFailureAuditor implements LoginFailureAuditor{

	@Override
	public void recordFailure(String key) {

	}

	@Override
	public boolean isLockedOut(String key) {
		return false;
	}

	@Override
	public void resetFailures(String key) {

	}

	@Override
	public int getFailureCount(String key) {
		return 0;
	}

	@Override
	public void cleanupExpiredLockouts() {

	}
}
