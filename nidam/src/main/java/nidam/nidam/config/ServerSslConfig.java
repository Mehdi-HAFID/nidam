package nidam.nidam.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures HTTPS for the Nidam resource server when HTTPS is enabled.
 * <p>
 * HTTPS is controlled by the {@code nidam.protocol} configuration property,
 * which defaults to {@code http}. When set to {@code https}, this configuration
 * enables SSL on the resource server using the certificate and private key
 * provided in the {@code ./https} directory.
 * </p>
 *
 * <p>
 * The certificate is expected at {@code ./https/host.pem} and the corresponding
 * private key at {@code ./https/host-key.pem}.
 * </p>
 *
 * <p>
 * SSL is configured programmatically so that {@code nidam.protocol} remains
 * the single switch used to enable or disable HTTPS for the resource server.
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "nidam.protocol", havingValue = "https")
public class ServerSslConfig {

	/**
	 * Configures the servlet web server to use SSL when
	 * {@code nidam.protocol=https}.
	 *
	 * @return a web server customizer that enables SSL using the configured certificate and private key
	 */
	@Bean
	public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> sslCustomizer() {

		return factory -> {
			Ssl ssl = new Ssl();
			ssl.setEnabled(true);
			ssl.setCertificate("file:./https/host.pem");
			ssl.setCertificatePrivateKey("file:./https/host-key.pem");

			factory.setSsl(ssl);
		};
	}
}
