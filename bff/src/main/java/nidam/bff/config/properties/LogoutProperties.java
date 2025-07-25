package nidam.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "logout")
public class LogoutProperties {

	private String authServerUri;
	private String tokenHintParamName;
	private String postRedirectParamName;
	private String clientIdParamName;
	private String successRedirectHeader;
	private String successRedirectDefaultUri;

	public String getAuthServerUri() {
		return authServerUri;
	}

	public void setAuthServerUri(String authServerUri) {
		this.authServerUri = authServerUri;
	}

	public String getTokenHintParamName() {
		return tokenHintParamName;
	}

	public void setTokenHintParamName(String tokenHintParamName) {
		this.tokenHintParamName = tokenHintParamName;
	}

	public String getPostRedirectParamName() {
		return postRedirectParamName;
	}

	public void setPostRedirectParamName(String postRedirectParamName) {
		this.postRedirectParamName = postRedirectParamName;
	}

	public String getClientIdParamName() {
		return clientIdParamName;
	}

	public void setClientIdParamName(String clientIdParamName) {
		this.clientIdParamName = clientIdParamName;
	}

	public String getSuccessRedirectHeader() {
		return successRedirectHeader;
	}

	public void setSuccessRedirectHeader(String successRedirectHeader) {
		this.successRedirectHeader = successRedirectHeader;
	}

	public String getSuccessRedirectDefaultUri() {
		return successRedirectDefaultUri;
	}

	public void setSuccessRedirectDefaultUri(String successRedirectDefaultUri) {
		this.successRedirectDefaultUri = successRedirectDefaultUri;
	}
}
