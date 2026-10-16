package nidam.tokengenerator.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures HTTPS for the Nidam token generator when HTTPS is enabled.
 * <p>
 * HTTPS is controlled by the {@code ha.token-generator.https} configuration property,
 * which defaults to {@code false}. When set to {@code true}, this configuration
 * enables SSL on the token generator using the certificate and private key
 * provided in the {@code ./https} directory.
 * </p>
 *
 * <p>
 * The certificate is expected at {@code ./https/token-generator.pem} and the corresponding
 * private key at {@code ./https/token-generator-key.pem}.
 * </p>
 *
 * <p>
 * SSL is configured programmatically so that {@code ha.token-generator.https} remains
 * the single switch used to enable or disable HTTPS for the token generator.
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "ha.token-generator.https", havingValue = "true")
public class ServerSslConfig {

	/**
	 * Configures the servlet web server to use SSL when
	 * {@code ha.token-generator.https=true}.
	 *
	 * @return a web server customizer that enables SSL using the configured certificate and private key
	 */
	@Bean
	public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> sslCustomizer() {

		return factory -> {

			Ssl ssl = new Ssl();
			ssl.setEnabled(true);
			ssl.setCertificate("file:./https/token-generator.pem");
			ssl.setCertificatePrivateKey("file:./https/token-generator-key.pem");

			factory.setSsl(ssl);
		};
	}
}
