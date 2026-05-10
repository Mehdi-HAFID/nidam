package nidam.registration.startup;

import nidam.registration.services.AuthorityServiceSpec;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Startup task responsible for initializing authorities in the persistence layer.
 *
 * <p>This component delegates to {@link AuthorityServiceSpec} to ensure that all
 * configured authorities are present in the database at application startup.</p>
 *
 * <p>Execution order is controlled via {@link Order}. A lower value means higher priority,
 * ensuring this task runs early in the startup sequence.</p>
 *
 * <p>Supports both SQL and MongoDB implementations transparently via
 * {@link AuthorityServiceSpec}.</p>
 */
@Component
@Order(1)
public class AuthorityInitializer implements StartupTask {

	private final AuthorityServiceSpec authorityService;

	public AuthorityInitializer(AuthorityServiceSpec authorityService) {
		this.authorityService = authorityService;
	}

	@Override
	public void run() {
		authorityService.addToDatabase();
	}
}
