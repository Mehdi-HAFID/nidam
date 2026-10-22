package nidam.bff.security;

import jakarta.annotation.Nonnull;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizationSuccessHandler;
import org.springframework.security.oauth2.client.RefreshOidcUserReactiveOAuth2AuthorizationSuccessHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Handles successful OAuth2 token refreshes for the BFF by synchronizing the
 * refreshed OpenID Connect (OIDC) ID token with the security context and rotating
 * the WebSession identifier.
 *
 * <p>This handler composes Spring Security's
 * {@link RefreshOidcUserReactiveOAuth2AuthorizationSuccessHandler} with Nidam's
 * session-ID rotation behavior. The native handler validates a refreshed ID token
 * and updates the {@link org.springframework.security.core.context.SecurityContext}
 * when the refresh response contains a new ID token.</p>
 *
 * <p>After the native OIDC handler completes successfully, this handler rotates
 * the current {@link WebSession} identifier using
 * {@link WebSession#changeSessionId()}. Rotation is performed even when the refresh
 * response does not contain a new ID token, provided the current
 * {@link ServerWebExchange} is available.</p>
 *
 * <h2>Exchange resolution</h2>
 * <p>The current {@link ServerWebExchange} is obtained first from the Reactor
 * {@code Context}, as populated by {@link ServerWebExchangeContextFilter}, and
 * then from the handler's attributes using the key
 * {@code ServerWebExchange.class.getName()}.</p>
 *
 * <p>If no exchange is available, the handler logs a warning and delegates to
 * Spring Security's native OIDC handler without rotating the session identifier.
 * Consequently, registering {@link ServerWebExchangeContextFilter} is important
 * for ensuring that session rotation occurs along the BFF's token-relay path.</p>
 *
 * <h2>Configuration</h2>
 * <p>Register this handler with a
 * {@link org.springframework.security.oauth2.client.RefreshTokenReactiveOAuth2AuthorizedClientProvider}
 * through its authorization success handler. It supplements Spring Security's
 * built-in OIDC refresh handling rather than replacing the refresh-token provider
 * itself.</p>
 *
 * <p>The handler uses a {@link WebSessionServerSecurityContextRepository} so that
 * the refreshed security context is persisted through the same WebSession
 * attribute used by Spring Security. This supports both in-memory and
 * Spring Session Redis-backed deployments.</p>
 *
 * @see RefreshOidcUserReactiveOAuth2AuthorizationSuccessHandler
 * @see org.springframework.security.oauth2.client.RefreshTokenReactiveOAuth2AuthorizedClientProvider
 * @see ServerWebExchangeContextFilter
 * @see WebSession#changeSessionId()
 */
public class OidcRefreshSessionRotationSuccessHandler implements ReactiveOAuth2AuthorizationSuccessHandler {
	private final static Logger log = Logger.getLogger(OidcRefreshSessionRotationSuccessHandler.class.getName());

	private final RefreshOidcUserReactiveOAuth2AuthorizationSuccessHandler oidcSuccessHandler;

	public OidcRefreshSessionRotationSuccessHandler() {
		ServerSecurityContextRepository securityContextRepository = new WebSessionServerSecurityContextRepository();
		oidcSuccessHandler = new RefreshOidcUserReactiveOAuth2AuthorizationSuccessHandler();
		oidcSuccessHandler.setServerSecurityContextRepository(securityContextRepository);
	}

	@Override
	@Nonnull
	public Mono<Void> onAuthorizationSuccess(@NonNull OAuth2AuthorizedClient authorizedClient, @NonNull Authentication principal,
											 @NonNull Map<String, Object> attributes) {
		return Mono.deferContextual(contextView -> {

			// First try Nidam's ServerWebExchangeContextFilter.
			Optional<ServerWebExchange> exchangeOpt = ServerWebExchangeContextFilter.getExchange(contextView);

			// Also support an exchange supplied through the handler attributes.
			if (exchangeOpt.isEmpty()) {
				Object exchangeAttribute = attributes.get(ServerWebExchange.class.getName());

				if (exchangeAttribute instanceof ServerWebExchange exchange) {
					exchangeOpt = Optional.of(exchange);
				}
			}

			if (exchangeOpt.isEmpty()) {
				log.warning("No ServerWebExchange available after OAuth2 refresh; session-ID rotation is skipped");

				// Still allow Spring's native OIDC handler to process any exchange available through its own mechanisms.
				return oidcSuccessHandler.onAuthorizationSuccess(authorizedClient, principal, attributes);
			}

			ServerWebExchange exchange = exchangeOpt.get();

			// The native handler expects this particular attribute key.
			Map<String, Object> handlerAttributes = new HashMap<>(attributes);

			handlerAttributes.put(ServerWebExchange.class.getName(), exchange);

			// Synchronize and validate the refreshed ID token first. Only after that succeeds, rotate the session identifier.
			return oidcSuccessHandler.onAuthorizationSuccess(authorizedClient, principal, Collections.unmodifiableMap(handlerAttributes))
					.then(exchange.getSession()
							.flatMap(WebSession::changeSessionId)
							.then());
		});
	}
}
