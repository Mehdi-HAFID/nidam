package nidam.tokengenerator.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "ttl")
public class TTLProperties {

	private Duration idToken;
	private Duration accessToken;
	private Duration refreshToken;
	private Duration authCode;

	public Duration getIdToken() {
		return idToken;
	}

	public void setIdToken(Duration idToken) {
		this.idToken = idToken;
	}

	public Duration getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(Duration accessToken) {
		this.accessToken = accessToken;
	}

	public Duration getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(Duration refreshToken) {
		this.refreshToken = refreshToken;
	}

	public Duration getAuthCode() {
		return authCode;
	}

	public void setAuthCode(Duration authCode) {
		this.authCode = authCode;
	}
}
