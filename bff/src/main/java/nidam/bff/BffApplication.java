package nidam.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;

/**
 * Entry point for the BFF (Backend-for-Frontend) application.
 * <p>
 * {@code SessionAutoConfiguration} is excluded unconditionally, independent of
 * {@code nidam.session-mode}. Boot's Redis session autoconfiguration backs off only when a
 * {@code ReactiveSessionRepository} bean already exists — not a {@code WebSessionManager} bean,
 * which is what {@code tomcat} mode defines — so left included, it fires in both modes whenever
 * {@code spring-session-data-redis} is on the classpath and a Redis connection factory exists,
 * and collides with the manual {@code webSessionManager} bean {@code tomcat} mode defines (both
 * register a bean literally named {@code webSessionManager}, failing startup with a
 * {@code BeanDefinitionOverrideException}).
 * <p>
 * With it excluded, Redis-backed sessions are only ever enabled explicitly, via
 * {@code @EnableRedisWebSession} on {@code RedisSessionConfig} — itself gated on
 * {@code nidam.session-mode=redis} — so the two modes can no longer conflict at startup
 * regardless of classpath contents.
 * <p>
 * {@code RedisHealthContributorAutoConfiguration} and
 * {@code RedisReactiveHealthContributorAutoConfiguration} are excluded for the same reason
 * as in the token generator: both gate purely on bean <em>type</em> presence —
 * {@code RedisConnectionFactory} for the blocking one, {@code ReactiveRedisConnectionFactory}
 * for the reactive one — not on {@code nidam.session-mode}. Because {@code RedisAutoConfiguration}
 * always registers a Lettuce-backed connection factory that implements <b>both</b> interfaces on
 * the same bean, both conditions are satisfied together regardless of the BFF being a WebFlux
 * (reactive) application; excluding only the reactive one would still leave the blocking
 * contributor active and able to flip {@code /actuator/health} to {@code DOWN}. In
 * {@code tomcat} mode the BFF has no functional dependency on Redis, so that would be a false
 * alarm rather than a meaningful signal. The equivalent indicator is re-registered manually,
 * gated to {@code session-mode=redis}, as {@code redisHealthIndicator} alongside
 * {@code RedisSessionConfig}, so the resulting {@code /actuator/health} shape is unchanged from
 * Boot's default whenever Redis genuinely backs the session store.
 *
 * @see nidam.bff.config.RedisSessionConfig
 */
@SpringBootApplication(exclude = { SessionAutoConfiguration.class,
		RedisHealthContributorAutoConfiguration.class, RedisReactiveHealthContributorAutoConfiguration.class})
public class BffApplication {

	public static void main(String[] args) {
		SpringApplication.run(BffApplication.class, args);
	}

}
