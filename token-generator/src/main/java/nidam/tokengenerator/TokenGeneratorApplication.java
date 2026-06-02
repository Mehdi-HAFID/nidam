package nidam.tokengenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The main entry point for the Token Generator application.
 * <p>
 * <b>Important Configuration Note:</b> {@link SessionAutoConfiguration} is explicitly excluded.
 * This prevents Spring Boot from aggressively overriding the native Tomcat session manager
 * just because the Redis dependency is present on the classpath. By excluding this, we allow
 * the application to dynamically toggle between native Tomcat in-memory sessions and Spring
 * Session (Redis) based on custom application properties.
 * </p>
 *
 * @see nidam.tokengenerator.config.RedisSessionConfig
 */
@SpringBootApplication(exclude = { SessionAutoConfiguration.class })
@EnableScheduling
public class TokenGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TokenGeneratorApplication.class, args);
	}

}
