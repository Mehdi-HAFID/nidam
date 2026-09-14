package nidam.micronaut.controller;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Collections;
import java.util.List;

@Controller("/me")
public class MeController {

	@Get
	@Secured(SecurityRule.IS_ANONYMOUS)
	public UserInfoDto getMe(@Nullable Authentication auth) {
		if (auth == null) {
			return new UserInfoDto("", "", Collections.emptyList(), Long.MAX_VALUE);
		}

		String username = auth.getName();
		String email = (String) auth.getAttributes().getOrDefault("email", "");

		// Micronaut automatically maps the 'authorities' claim to roles due to our application.yml
		List<String> authorities = List.copyOf(auth.getRoles());

		Object expObj = auth.getAttributes().get("exp");
		long exp = Long.MAX_VALUE;
		if (expObj instanceof Number numberExp) {
			exp = numberExp.longValue();
		}

		return new UserInfoDto(username, email, authorities, exp);
	}

	@Serdeable
	public record UserInfoDto(String username, String email, List<String> authorities, Long exp) {
	}
}