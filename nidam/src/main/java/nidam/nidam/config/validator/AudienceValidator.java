package nidam.nidam.config.validator;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

	private final String expectedAudience;

	public AudienceValidator(String expectedAudience) {
		this.expectedAudience = expectedAudience;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		List<String> audList = jwt.getAudience();
		if (audList == null || !audList.contains(expectedAudience)) {
			return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience value", null));
		}
		return OAuth2TokenValidatorResult.success();
	}
}
