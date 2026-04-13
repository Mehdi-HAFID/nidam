package nidam.tokengenerator.service.scheduled;

import nidam.tokengenerator.service.LoginFailureAuditor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

/**
 * Scheduled task that periodically cleans up expired login lockouts.
 * <p>
 * Delegates to a {@link LoginFailureAuditor} to remove stale failure records,
 * ensuring that temporary lockouts do not persist beyond their intended duration.
 * <p>
 * This helps maintain a responsive and fair authentication flow,
 * especially in systems with aggressive rate-limiting policies.
 */
@Component
public class LoginFailureCleanupTask {
	private final Logger log = Logger.getLogger(LoginFailureCleanupTask.class.getName());

	private final LoginFailureAuditor auditor;

	public LoginFailureCleanupTask(LoginFailureAuditor auditor) {
		this.auditor = auditor;
	}

	/**
	 * Invokes cleanup of expired lockout entries at a fixed interval.
	 * <p>
	 * The interval is configurable via the {@code rate-limiter.cleanup-interval-ms} property,
	 * defaulting to 60 seconds if unspecified.
	 * <p>
	 * Logs the cleanup event for visibility and delegates the actual removal
	 * to the {@link LoginFailureAuditor}.
	 */
	@Scheduled(fixedRateString = "${rate-limiter.cleanup-interval-ms:60000}") // every 1 minute
	public void cleanup() {
		log.info("Cleaning up expired lockouts");
		auditor.cleanupExpiredLockouts();
	}
}
