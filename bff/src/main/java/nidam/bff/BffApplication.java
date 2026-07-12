package nidam.bff;

import org.springframework.boot.SpringApplication;
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
 */
@SpringBootApplication(exclude = { SessionAutoConfiguration.class })
public class BffApplication {

	public static void main(String[] args) {
		SpringApplication.run(BffApplication.class, args);
	}

}
