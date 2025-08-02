package nidam.nidam.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@RestController
public class MeController {
	private static final Logger log = Logger.getLogger(MeController.class.getName());

	// I can use auth.getName() for email, but since I will populate the token payload with standard OpenID Connect Claims may as well
	@GetMapping("/me")
	public UserInfoDto getMe(Authentication auth) {
		if (auth instanceof JwtAuthenticationToken jwtAuth) {
			log.info("Name: '" + jwtAuth.getName() + "' getAuthorities: '" + jwtAuth.getAuthorities() + "' Claims: " + jwtAuth.getToken().getClaims());
			final String email = (String) jwtAuth.getTokenAttributes()
					.getOrDefault(StandardClaimNames.EMAIL, "");
			final List<String> authorities = auth.getAuthorities()
					.stream()
					.map(GrantedAuthority::getAuthority)
					.toList();
			final Long exp = Optional.ofNullable(jwtAuth.getTokenAttributes()
					.get(JwtClaimNames.EXP)).map(expClaim -> {
				if (expClaim instanceof Long lexp) {
					return lexp;
				}
				if (expClaim instanceof Instant iexp) {
					return iexp.getEpochSecond();
				}
				if (expClaim instanceof Date dexp) {
					return dexp.toInstant().getEpochSecond();
				}
				return Long.MAX_VALUE;
			}).orElse(Long.MAX_VALUE);
			return new UserInfoDto(auth.getName(), email, authorities, exp);
		}
		return UserInfoDto.ANONYMOUS;
	}

	/**
	 * @param username    a unique identifier for the resource owner in the token (sub claim by default)
	 * @param email       OpenID email claim
	 * @param authorities Spring authorities resolved for the authentication in the security context
	 * @param exp         seconds from 1970-01-01T00:00:00Z UTC until the specified UTC date/time when the access token expires
	 */

	public static record UserInfoDto(String username, String email, List<String> authorities, Long exp) {
		public static final UserInfoDto ANONYMOUS = new UserInfoDto("", "", List.of(), Long.MAX_VALUE);
	}
}