package nidam.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * {@code SecurityConfig} defines the security configuration for the BFF (Backend-for-Frontend) layer
 * using Spring Security WebFlux.
 *
 * <p>This class sets up:</p>
 * <ul>
 *     <li>OAuth2 login and token relay</li>
 *     <li>Session-based authentication</li>
 *     <li>Custom logout flow with RP-Initiated logout support</li>
 *     <li>CSRF protection using a cookie-based token repository</li>
 * </ul>
 *
 * <p>It also exposes a {@link WebFilter} to ensure the CSRF token is propagated to the frontend
 * as a readable cookie, and handles secure logout redirection to the OIDC Provider.</p>
 */
@Configuration
public class SecurityConfig {

	private final static Logger log = Logger.getLogger(SecurityConfig.class.getName());

	private final ServerAuthenticationSuccessHandler postLoginRedirectHandler;
	private final ServerLogoutSuccessHandler logoutRedirectHandler;

	public static final String COOKIE_XSRF_TOKEN = "XSRF-TOKEN";
	public static final String BFF_LOGOUT_ENDPOINT = "/logout";

	private static final String[] UNAUTHENTICATED_PATHS =
			{"/api/me", "/login/**", "/oauth2/**", "/error", "/post-logout"};

	/**
	 * Constructs a new {@code SecurityConfig} with required components.
	 *
	 * @param postLoginRedirectHandler the custom OAuth2 login success handler
	 * @param logoutRedirectHandler    properties related to the logout flow
	 */
	public SecurityConfig(ServerAuthenticationSuccessHandler postLoginRedirectHandler, ServerLogoutSuccessHandler logoutRedirectHandler) {
		this.postLoginRedirectHandler = postLoginRedirectHandler;
		this.logoutRedirectHandler = logoutRedirectHandler;
	}

	/**
	 * Defines the Spring Security filter chain for the BFF.
	 *
	 * <ul>
	 *     <li>Permits unauthenticated access to login endpoints, actuator, and logout</li>
	 *     <li>Protects other paths with authentication</li>
	 *     <li>Enables OAuth2 login and client support</li>
	 *     <li>Applies CSRF protection using a cookie</li>
	 * </ul>
	 *
	 * @param http    the reactive HTTP security config
	 * @param clients OAuth2 client repository (used for login)
	 * @return the configured security filter chain
	 */

	/**
	 * Configures the Spring Security filter chain for the BFF application.
	 *
	 * <p>This setup secures the BFF endpoints, integrates with the OAuth2
	 * authorization server, and ensures CSRF protection for state-changing
	 * requests.</p>
	 *
	 * <ul>
	 *   <li>Allows unauthenticated access to:
	 *       <ul>
	 *           <li>login endpoints</li>
	 *           <li>actuator endpoints</li>
	 *           <li>the BFF logout endpoint (POST)</li>
	 *       </ul>
	 *   </li>
	 *   <li>Requires authentication for all other requests</li>
	 *   <li>Enables OAuth2 login with a custom {@code AuthenticationSuccessHandler}</li>
	 *   <li>Enables OAuth2 client support (including Token Relay)</li>
	 *   <li>Configures logout at the BFF endpoint with a custom {@code ServerLogoutSuccessHandler}</li>
	 *   <li>Applies CSRF protection using a cookie-based {@code CsrfTokenRepository}</li>
	 * </ul>
	 *
	 * @param http    the reactive HTTP security configuration
	 * @param clients the OAuth2 client registration repository
	 * @return the configured {@link SecurityWebFilterChain}
	 */
	@Bean
	public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http, ReactiveClientRegistrationRepository clients) {
		http
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers(UNAUTHENTICATED_PATHS).permitAll()
						.pathMatchers(HttpMethod.POST, BFF_LOGOUT_ENDPOINT).permitAll()
						.anyExchange().authenticated()
				)
				// Enables login with the authorization server
				.oauth2Login(oauth2 -> oauth2.authenticationSuccessHandler(postLoginRedirectHandler))
				.oauth2Client(Customizer.withDefaults()) // enables the OAuth2 client support, Enables TokenRelay

				.logout(logout -> logout
						.logoutUrl(BFF_LOGOUT_ENDPOINT)  // or whatever logout endpoint you use
						.logoutSuccessHandler(logoutRedirectHandler)
				)

				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
						.csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
				);

		return http.build();
	}

	/**
	 * Adds a WebFilter that ensures the CSRF token is available as a cookie readable by JavaScript.
	 * If the CSRF token is present and differs from the existing cookie, it updates the cookie.
	 *
	 * @return a WebFilter that manages CSRF token propagation to the frontend
	 */
	@Bean
	public WebFilter csrfTokenWebFilter() {
		return (exchange, chain) -> exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty())
				.cast(CsrfToken.class)
				.switchIfEmpty(
						exchange.getSession().then(Mono.defer(() ->
								exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty()).cast(CsrfToken.class)
						))
				)
				.flatMap(token -> {

					// Only set the cookie if it's not already present or changed
					String currentCookie = exchange.getRequest().getCookies().getFirst(COOKIE_XSRF_TOKEN) != null
							? exchange.getRequest().getCookies().getFirst(COOKIE_XSRF_TOKEN).getValue()
							: null;
					log.info("csrfTokenWebFilter() currentCookie: " + currentCookie);

					if (!token.getToken().equals(currentCookie)) {
						ResponseCookie cookie = ResponseCookie.from(COOKIE_XSRF_TOKEN, token.getToken())
								.path("/") // match the path of your app
								.sameSite("Lax")
								.httpOnly(false) // must be accessible to JS
								.secure(false) // set to true if using HTTPS
								.build();
						log.info("csrfTokenWebFilter()  Setting new XSRF-TOKEN cookie: {} " + cookie);
						exchange.getResponse().addCookie(cookie);
					}
					return chain.filter(exchange);
				})
				.switchIfEmpty(chain.filter(exchange));
	}



	/*
	  This code fixes the issue where session held in memory is evicted after 30 minutes of inactivity:
	  login, token valid for 12 hours, user does not interact with bff for 30 minutes, the bff remove Token <-> SESSION relation from memory,

	  * User returns, SPA sends SESSION cookie, but it's no longer valid → session is gone
	  * Spring creates a new session, with no token
	  * TokenRelay sees no token → skips adding Authorization header
	  * Resource server sees no bearer token → 401

	  After 30 minutes of inactivity, the session is evicted from memory due to the default maxIdleTime = 30m
	  WebFilter that sets session max idle time to 12 hours.
	  submitted an issue https://github.com/spring-projects/spring-framework/issues/35240
	 */

	/**
	 * Configures a {@link WebFilter} that sets a maximum idle timeout of 12 hours on each WebFlux session,
	 * but only once per session lifecycle and only if the session has already started.
	 * <p>
	 * This is necessary to keep the session (and therefore the {@code SESSION} cookie and any stored
	 * {@link org.springframework.security.oauth2.client.OAuth2AuthorizedClient} including the access token)
	 * alive for as long as the access token is valid. By default, Spring WebFlux sessions have a
	 * {@code maxIdleTime} of 30 minutes, which would cause token relay to silently fail after inactivity.
	 * </p>
	 *
	 * <p>
	 * To prevent creating sessions unnecessarily (e.g., for unauthenticated requests like logout),
	 * this filter only applies to sessions that have already been started using {@code session.isStarted()}.
	 * Additionally, the timeout is only set once per session by checking a session-scoped attribute flag.
	 * </p>
	 *
	 * @return a {@link WebFilter} that sets session idle timeout to 12 hours, only once per session
	 */
	@Bean
	public WebFilter sessionTimeoutWebFilter() {
		return (exchange, chain) -> {
			Mono<WebSession> sessionMono = exchange.getSession()
					.filter(WebSession::isStarted)
					.doOnNext(session -> {
						if (session.getAttribute("SESSION_TIMEOUT_SET") == null) {
							session.setMaxIdleTime(Duration.ofHours(12));
							session.getAttributes().put("SESSION_TIMEOUT_SET", true);
							log.info("Session timeout set to 12h for session ID:" + session.getId());
//							log.info("Max idle time: " + session.getMaxIdleTime());
//							log.info("Creation time: " + session.getCreationTime());
						}

					});
			return sessionMono.then(chain.filter(exchange));
		};
	}


	// waiting for the auth server bug to be fixed to uncomment this
	// refreshing cookies values when bff automatically use refresh token to get a new token.
//	/**
//	 * Configures a {@link WebFilter} that detects when the OAuth2 access token has been refreshed
//	 * and reacts by regenerating the session ID.
//	 *
//	 * <p>This is important to avoid session fixation attacks and to ensure consistency in CSRF and session
//	 * data. When a new access token is issued (via refresh token), the filter compares the current access token
//	 * with the one stored in the session under {@code lastAccessToken}.</p>
//	 *
//	 * <p>If the token has changed, the new one is saved into the session and {@link WebSession#changeSessionId()}
//	 * is invoked to force session regeneration. This ensures the server-side session and the client's access token
//	 * remain synchronized.</p>
//	 *
//	 * <p>The filter executes after Spring Security filters (order 200) and uses the provided
//	 * {@link ReactiveOAuth2AuthorizedClientService} to look up the currently authorized client.</p>
//	 *
//	 * @param clientService the authorized client service used to retrieve the access token
//	 * @return a {@link WebFilter} that refreshes the session on access token refresh
//	 */
//	@Bean
//	@Order(200) // after Spring Security filters
//	public WebFilter refreshSessionOnAccessTokenRefreshFilter(ReactiveOAuth2AuthorizedClientService clientService) {
//		return (exchange, chain) -> exchange.getPrincipal()
//				.cast(OAuth2AuthenticationToken.class)
//				.flatMap(auth -> clientService.loadAuthorizedClient(auth.getAuthorizedClientRegistrationId(), auth.getName())
//						.flatMap(client -> {
//							String currentToken = client.getAccessToken().getTokenValue();
//							log.info("refreshSessionOnAccessTokenRefreshFilter currentToken: " + currentToken);
//							return exchange.getSession().flatMap(session -> {
//								String previousToken = (String) session.getAttributes().get("lastAccessToken");
//								log.info("refreshSessionOnAccessTokenRefreshFilter previousToken: " + previousToken);
//								if (previousToken != null && !currentToken.equals(previousToken)) {
//									log.info("Token was refreshed!");
//									// Token was refreshed!
//									session.getAttributes().put("lastAccessToken", currentToken);
//
//									// START this code is a workaround the exception thrown by auth server during logout after a refresh token,
//									// yet even when sending the updated id_token_hint the exception still happens. this section temporarily here
//									// to remove before deploying v 2. add this to the .docx documentation
////									// ✅ Also update the ID Token using OidcUser
////									if (auth.getPrincipal() instanceof OidcUser oidcUser) {
////										String idToken = oidcUser.getIdToken().getTokenValue();
////										log.info("saving idTokenHint (in session as originalIdToken) in refreshSessionOnAccessTokenRefreshFilter: " + idToken);
////										session.getAttributes().put("originalIdToken", idToken);
////										log.info("Updated ID token in session in refreshSessionOnAccessTokenRefreshFilter: " + idToken);
////									}
//									// END
//
//									return session.changeSessionId().then(chain.filter(exchange));
//								}
//								return chain.filter(exchange);
//							});
//						})
//				).switchIfEmpty(chain.filter(exchange));
//	}



}
