package nidam.registration.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "custom")
public class AllowedCorsUriProperties {
	private List<String> allowedCorsUri;

	public List<String> getAllowedCorsUri() {
		return allowedCorsUri;
	}

	public void setAllowedCorsUri(List<String> allowedCorsUri) {
		this.allowedCorsUri = allowedCorsUri;
	}
}
