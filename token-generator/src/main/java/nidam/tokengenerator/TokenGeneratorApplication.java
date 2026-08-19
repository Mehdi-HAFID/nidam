package nidam.tokengenerator;

import nidam.tokengenerator.redis.config.RedisOAuth2Config;
import nidam.tokengenerator.redis.config.RedisSessionConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration;
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
 *     Spring Boot auto-configuration. It is completely unaffected by the exclusions below.</li>
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
 * <h3>Why {@code RedisHealthContributorAutoConfiguration} and
 * {@code RedisReactiveHealthContributorAutoConfiguration} are excluded</h3>
 * <p>
 * Both autoconfiguration classes register a Redis {@code /actuator/health} contributor
 * based purely on bean <em>type</em> presence — {@code RedisConnectionFactory} for the
 * blocking one, {@code ReactiveRedisConnectionFactory} for the reactive one — with no
 * regard for {@code nidam.session-mode}. Since {@code RedisAutoConfiguration} always
 * registers a Lettuce-backed connection factory when the Redis starter is on the
 * classpath (Lettuce's {@code LettuceConnectionFactory} implements <b>both</b>
 * interfaces on the same bean, and {@code reactor-core} is transitively present), both
 * conditions are satisfied even though this application is a purely blocking Tomcat
 * servlet app. Excluding only one leaves the other active and still capable of dragging
 * {@code /actuator/health} to {@code DOWN}.
 * </p>
 * <p>
 * In {@code tomcat} mode the token generator has no functional dependency on Redis
 * whatsoever, so a Redis outage flipping health status here is a false alarm rather than
 * a meaningful signal — this service is purely internal, with nothing consuming that
 * status to make a routing or failover decision. Both autoconfigurations are excluded
 * unconditionally, and the equivalent indicator is re-registered manually, gated to
 * {@code session-mode=redis}, as {@code redisHealthIndicator} in {@link RedisOAuth2Config}.
 * </p>
 *
 * @see RedisSessionConfig
 * @see RedisOAuth2Config
 */
@SpringBootApplication(exclude = { SessionAutoConfiguration.class,
		RedisHealthContributorAutoConfiguration.class, RedisReactiveHealthContributorAutoConfiguration.class})
@EnableScheduling
public class TokenGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TokenGeneratorApplication.class, args);
	}

}
