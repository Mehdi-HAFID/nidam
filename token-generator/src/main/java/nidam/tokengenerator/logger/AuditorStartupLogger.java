package nidam.tokengenerator.logger;

import jakarta.annotation.PostConstruct;
import nidam.tokengenerator.service.LoginFailureAuditor;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

/**
 * Logs the active {@link LoginFailureAuditor} implementation at application startup.
 * <p>
 * Useful for verifying which auditor strategy is currently wired into the system,
 * especially in environments with multiple implementations or conditional beans.
 */
@Component
public class AuditorStartupLogger {

	private static final Logger log = Logger.getLogger(AuditorStartupLogger.class.getName());

	private final LoginFailureAuditor auditor;

	public AuditorStartupLogger(LoginFailureAuditor auditor) {
		this.auditor = auditor;
	}

	/**
	 * Logs the class name of the active {@link LoginFailureAuditor} implementation.
	 * <p>
	 * This method is automatically invoked after dependency injection completes,
	 * providing visibility into which auditor is currently in use.
	 */
	@PostConstruct
	public void logActiveAuditor() {
		log.info("StartupLogger Active LoginFailureAuditor: " + auditor.getClass().getSimpleName());
	}
}

