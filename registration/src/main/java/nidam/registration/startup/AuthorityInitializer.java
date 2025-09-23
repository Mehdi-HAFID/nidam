package nidam.registration.startup;

import nidam.registration.services.AuthorityService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AuthorityInitializer implements StartupTask {

	private final AuthorityService authorityService;

	public AuthorityInitializer(AuthorityService authorityService) {
		this.authorityService = authorityService;
	}

	@Override
	public void run() {
		authorityService.addToDatabase();
	}
}
