package nidam.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;

/**
 * Forces the OAuth2 client's authorized-client storage onto the reactive {@code WebSession},
 * rather than the disconnected in-memory store Spring Security falls back to by default.
 * <p>
 * Without this bean, {@code TokenRelayGatewayFilterFactory} resolves whatever
 * {@link org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager} happens to be in context. Spring Security
 * auto-registers a default one, but only once a {@link ServerOAuth2AuthorizedClientRepository}
 * bean exists to back it — absent that, the access token is instead persisted via
 * {@code InMemoryReactiveOAuth2AuthorizedClientService}, a plain in-memory map with no relation
 * to the session at all. That means the token would never travel with the session cookie,
 * never get cleaned up on logout/session invalidation, and never survive past a single instance.
 * <p>
 * Declaring this repository bean fixes both the write path (OAuth2 login success handler saves
 * the {@link org.springframework.security.oauth2.client.OAuth2AuthorizedClient} here directly)
 * and the read path (Spring Security's auto-registered manager is built around this same
 * repository), so the access token rides in the session alongside
 * {@code SPRING_SECURITY_CONTEXT} — and from there, wherever the session itself is stored
 * (in-memory or Redis).
 * <p>
 * <b>Intentionally not gated behind {@code nidam.session-mode}.</b> This concern is orthogonal
 * to which store backs the session — the token/session disconnect exists identically in
 * {@code tomcat} mode, it's just invisible there since nothing surfaces it. Keeping this bean
 * unconditional ensures both modes behave consistently.
 *
 * <p>TODO This could solve the refresh token logout problem: to test</p>
 */
@Configuration
public class OAuth2ClientConfig {

	/**
	 * Backs the OAuth2 client's authorized-client storage with the current {@code WebSession}
	 * instead of an unrelated in-memory map. See class-level Javadoc for why this matters.
	 */
	@Bean
	public ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
		return new WebSessionServerOAuth2AuthorizedClientRepository();
	}


}
