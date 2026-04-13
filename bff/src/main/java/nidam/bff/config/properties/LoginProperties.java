package nidam.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "login")
public class LoginProperties {

	private List<String> allowedRedirectUriPrefixes;
	private String successRedirectParamName;
	private String sessionRedirectUriAttribute;

	public List<String> getAllowedRedirectUriPrefixes() {
		return allowedRedirectUriPrefixes;
	}

	public void setAllowedRedirectUriPrefixes(List<String> allowedRedirectUriPrefixes) {
		this.allowedRedirectUriPrefixes = allowedRedirectUriPrefixes;
	}

	public String getSuccessRedirectParamName() {
		return successRedirectParamName;
	}

	public void setSuccessRedirectParamName(String successRedirectParamName) {
		this.successRedirectParamName = successRedirectParamName;
	}

	public String getSessionRedirectUriAttribute() {
		return sessionRedirectUriAttribute;
	}

	public void setSessionRedirectUriAttribute(String sessionRedirectUriAttribute) {
		this.sessionRedirectUriAttribute = sessionRedirectUriAttribute;
	}
}
