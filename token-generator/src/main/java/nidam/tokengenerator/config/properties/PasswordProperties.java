package nidam.tokengenerator.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "custom.password")
public class PasswordProperties {

	private List<String> encoders;

	private String idless;

	public List<String> getEncoders() {
		return encoders;
	}

	public void setEncoders(List<String> encoders) {
		this.encoders = encoders;
	}

	public String getIdless() {
		return idless;
	}

	public void setIdless(String idless) {
		this.idless = idless;
	}
}
