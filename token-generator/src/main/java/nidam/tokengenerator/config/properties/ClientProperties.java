package nidam.tokengenerator.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "client")
public class ClientProperties {

	private String id;
	private String secretHash;
	private String internalIdentifier;
	private String loginUri;
	private String logoutUri;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSecretHash() {
		return secretHash;
	}

	public void setSecretHash(String secretHash) {
		this.secretHash = secretHash;
	}

	public String getInternalIdentifier() {
		return internalIdentifier;
	}

	public void setInternalIdentifier(String internalIdentifier) {
		this.internalIdentifier = internalIdentifier;
	}

	public String getLoginUri() {
		return loginUri;
	}

	public void setLoginUri(String loginUri) {
		this.loginUri = loginUri;
	}

	public String getLogoutUri() {
		return logoutUri;
	}

	public void setLogoutUri(String logoutUri) {
		this.logoutUri = logoutUri;
	}
}
