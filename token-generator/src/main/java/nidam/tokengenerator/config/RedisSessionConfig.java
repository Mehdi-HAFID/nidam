package nidam.tokengenerator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import java.time.Duration;

/**
 * Configuration class that enables and customizes Spring Session backed by Redis.
 * <p>
 * This configuration is conditionally activated only when the application property
 * {@code nidam.session-mode} is set to {@code redis}. If the property is set to
 * {@code tomcat} (or omitted), this class is completely ignored, and the application
 * gracefully falls back to using Tomcat's native in-memory {@code HttpSession}.
 * </p>
 * <p>
 * Because Spring Boot's default {@code SessionAutoConfiguration} is disabled application-wide,
 * standard {@code spring.session.*} properties are not automatically bound to the repository.
 * Therefore, this class manually injects those values and applies them via a customizer.
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "redis")
@EnableRedisHttpSession
public class RedisSessionConfig {
	// When nidam.session-mode=redis, this class activates Spring Session backed by Redis.
	// When it is set to 'tomcat' (or anything else), this class is ignored,
	// and your app natively falls back to Tomcat's in-memory HttpSession.

	// 1. Inject the namespace, falling back to a default if missing
	@Value("${spring.session.redis.namespace:nidam:token-generator}")
	private String redisNamespace;

	// 2. Inject the timeout (Spring Boot automatically converts '12h' to a Duration)
	@Value("${spring.session.timeout:12h}")
	private Duration sessionTimeout;

	/**
	 * Customizes the {@link RedisSessionRepository} since default Spring Boot
	 * auto-configuration bindings are disabled.
	 *
	 * @return a {@link SessionRepositoryCustomizer} that applies the configured namespace
	 * and timeout duration to the Redis repository.
	 */
	@Bean
	public SessionRepositoryCustomizer<RedisSessionRepository> redisSessionCustomizer() {
		return repository -> {
			repository.setRedisKeyNamespace(redisNamespace);
			repository.setDefaultMaxInactiveInterval(sessionTimeout);
		};
	}
}
