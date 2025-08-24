package nidam.tokengenerator.service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class AuditorProperties {

	private int lockoutDuration;
	private int maxAttempts;

	public int getLockoutDuration() {
		return lockoutDuration;
	}
	public void setLockoutDuration(int lockoutDuration) {
		this.lockoutDuration = lockoutDuration;
	}
	public int getMaxAttempts() {
		return maxAttempts;
	}
	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}
}
