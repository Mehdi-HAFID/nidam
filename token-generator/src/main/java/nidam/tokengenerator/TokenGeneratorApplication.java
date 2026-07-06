package nidam.tokengenerator;

import nidam.tokengenerator.redis.config.RedisOAuth2Config;
import nidam.tokengenerator.redis.config.RedisSessionConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The main entry point for the Token Generator application.
 *
 * <h3>Session Mode</h3>
 * <p>
 * The application supports two session modes controlled by the {@code nidam.session-mode}
 * property:
 * </p>
 * <ul>
 *     <li><b>{@code tomcat}</b> — uses Tomcat's native in-memory {@link jakarta.servlet.http.HttpSession}.
 *     This is always available as part of the Tomcat servlet container and requires no
 *     Spring Boot auto-configuration. It is completely unaffected by the exclusion below.</li>
 *     <li><b>{@code redis}</b> — activates {@link RedisSessionConfig},
 *     which uses {@code @EnableRedisHttpSession} to replace Tomcat's native session handling
 *     with a Spring Session Redis-backed repository.</li>
 * </ul>
 *
 * <h3>Why {@link org.springframework.boot.autoconfigure.session.SessionAutoConfiguration} is excluded</h3>
 * <p>
 * {@code SessionAutoConfiguration} is Spring Session's auto-configuration — it detects
 * session store dependencies on the classpath (such as {@code spring-session-data-redis})
 * and automatically activates Spring Session, replacing Tomcat's native session handling.
 * Since the Redis dependency is always present on the classpath regardless of the active
 * session mode, without this exclusion Spring Boot would unconditionally activate Spring
 * Session even when {@code nidam.session-mode=tomcat} is set.
 * </p>
 * <p>
 * Excluding it does <b>not</b> affect Tomcat's native {@code HttpSession} in any way —
 * Tomcat session management is part of the servlet container itself, not controlled by
 * any Spring Boot auto-configuration class.
 * </p>
 *
 * @see RedisSessionConfig
 * @see RedisOAuth2Config
 */
@SpringBootApplication(exclude = { SessionAutoConfiguration.class })
@EnableScheduling
public class TokenGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TokenGeneratorApplication.class, args);
	}

}
