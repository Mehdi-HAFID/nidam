package nidam.micronaut.controller;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;

import java.util.Map;

@Controller
public class DemoController {

	@Get("/demo")
	@Secured({"manage-users", "manage-projects"}) // Requires AT LEAST ONE of these
	public Map<String, Object> demo(Authentication auth, @Header("Authorization") String authHeader) {
		String rawToken = authHeader != null ? authHeader.replace("Bearer ", "") : "";

		return Map.of(
				"tokenValue", rawToken,
				"claims", auth.getAttributes(),
				"authorities", auth.getRoles()
		);
	}

	@Get("/top-secret")
	@Secured("top-secret")
	public String topSecret() {
		return "Top secret information";
	}
}
