package nidam.bff.redirection;

import nidam.bff.config.properties.LoginProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.logging.Logger;

/**
 * Handles post-login redirection in a reactive Spring WebFlux BFF.
 *
 * <p>This component implements {@link ServerAuthenticationSuccessHandler} and is
 * invoked by Spring Security when an OAuth2 / OIDC login completes successfully.
 * It retrieves the previously stored post-login redirect URI from the
 * {@link org.springframework.web.server.WebSession} and redirects the user to
 * the intended frontend destination. If no valid redirect URI is found, it falls
 * back to a default URI (usually the root of the SPA).</p>
 *
 * <p>The allowed redirect URIs are validated against the configured prefixes
 * in {@link LoginProperties} to prevent open redirect vulnerabilities.</p>
 *
 * <p>Typical flow:</p>
 * <ol>
 *     <li>User clicks login in the SPA.</li>
 *     <li>A pre-login {@link org.springframework.web.server.WebFilter} stores the intended post-login URI in the session.</li>
 *     <li>Spring Security handles the OAuth2 authentication flow.</li>
 *     <li>Upon successful authentication, {@code onAuthenticationSuccess} reads the
 *     stored URI, validates it, and sends an HTTP 302 redirect to the SPA.</li>
 * </ol>
 */
@Component
public class PostLoginRedirectConfig implements ServerAuthenticationSuccessHandler {

	@Value("${react-proxy-uri}")
	private String defaultReactUri;

	private final Logger log = Logger.getLogger(PostLoginRedirectConfig.class.getName());

	private final LoginProperties loginProperties;
//	private final ReactiveOAuth2AuthorizedClientService authorizedClientService;

	public PostLoginRedirectConfig(LoginProperties loginProperties
//			, ReactiveOAuth2AuthorizedClientService authorizedClientService
	) {
		this.loginProperties = loginProperties;
//		this.authorizedClientService = authorizedClientService;
	}

	/**
	 * Called by Spring Security after a successful authentication.
	 *
	 * <p>Retrieves the post-login redirect URI from the session, validates it against
	 * allowed prefixes, and sends an HTTP 302 redirect to the client. If the URI is
	 * missing or untrusted, falls back to the default SPA URI.</p>
	 *
	 * @param webFilterExchange the current {@link WebFilterExchange} holding the
	 *                          {@link ServerWebExchange} and filter chain
	 * @param authentication    the authentication token representing the logged-in user
	 * @return a {@link Mono} that completes when the redirect response is sent
	 */
	@Override
	public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
		ServerWebExchange exchange = webFilterExchange.getExchange();
		return exchange.getSession()
				.flatMap(session -> {
					// pull out what we saved earlier (or fall back)

					String redirect = session.getAttribute(loginProperties.getSessionRedirectUriAttribute());
					log.info("onAuthenticationSuccess Redirect to: " + redirect);

					boolean isAllowed = redirect != null && loginProperties.getAllowedRedirectUriPrefixes().stream().anyMatch(redirect::startsWith);

					String finalRedirect = isAllowed ? redirect : defaultReactUri;

					if (!isAllowed) {
						log.warning("Untrusted or missing redirect URI in session. Falling back to default: " + finalRedirect);
					}

					ServerHttpResponse response = exchange.getResponse();
					response.setStatusCode(HttpStatus.FOUND);
					response.getHeaders().setLocation(URI.create(finalRedirect));
					return response.setComplete();
				});
	}

	// TODO IMPORTANT do not remove this is the version that support automatic refresh token, just waiting for the spring auth server bug to be fixed
//	@Override
//	public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
//		ServerWebExchange exchange = webFilterExchange.getExchange();
//
//		// Fallback for non-OAuth2 login
//		return exchange.getSession()
//				.flatMap(session -> authorizedClientService
//						.loadAuthorizedClient(
//								((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId(), authentication.getName())
//						.doOnNext(client -> {
//							String accessToken = client.getAccessToken().getTokenValue();
//							log.info("onAuthenticationSuccess accessToken: " + accessToken);
//							session.getAttributes().put("lastAccessToken", accessToken);
//						})
//						.then(Mono.defer(() -> {
//							String redirect = session.getAttribute(loginProperties.getSessionRedirectUriAttribute());
//							log.info("onAuthenticationSuccess Redirect to: " + redirect);
//
//							boolean isAllowed = redirect != null &&
//									loginProperties.getAllowedRedirectUriPrefixes().stream().anyMatch(redirect::startsWith);
//							String finalRedirect = isAllowed ? redirect : defaultReactUri;
//
//							if (!isAllowed) {
//								log.warning("Untrusted or missing redirect URI in session. Falling back to default: " + finalRedirect);
//							}
//
//							ServerHttpResponse response = exchange.getResponse();
//							response.setStatusCode(HttpStatus.FOUND);
//							response.getHeaders().setLocation(URI.create(finalRedirect));
//							return response.setComplete();
//						}))
//				);
//	}
}
