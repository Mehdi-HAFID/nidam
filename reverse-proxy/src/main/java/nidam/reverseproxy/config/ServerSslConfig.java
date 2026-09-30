package nidam.reverseproxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.server.ConfigurableReactiveWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures HTTPS for the Nidam reverse proxy when HTTPS is selected.
 * <p>
 * The protocol is controlled by the {@code nidam.protocol} configuration
 * property and defaults to {@code http}. When set to {@code https}, the
 * reverse proxy is configured with the local PEM certificate and private key
 * located in the {@code ./https} directory.
 * </p>
 *
 * <p>
 * SSL is configured programmatically because {@code nidam.protocol} is the
 * single switch used to enable or disable HTTPS for the reverse proxy.
 * </p>
 */
@Configuration
public class ServerSslConfig {

	/**
	 * Enables SSL on the reactive web server when {@code nidam.protocol=https}.
	 *
	 * @param protocol the configured Nidam protocol; defaults to {@code http}
	 * @return a web server customizer that enables SSL for HTTPS
	 */
	@Bean
	public WebServerFactoryCustomizer<ConfigurableReactiveWebServerFactory> sslCustomizer(@Value("${nidam.protocol:http}") String protocol) {

		return factory -> {

			if (!"https".equalsIgnoreCase(protocol)) {
				return;
			}

			Ssl ssl = new Ssl();
			ssl.setEnabled(true);
			ssl.setCertificate("file:./https/host.pem");
			ssl.setCertificatePrivateKey("file:./https/host-key.pem");

			factory.setSsl(ssl);
		};
	}
}
