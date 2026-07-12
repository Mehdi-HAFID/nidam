package nidam.bff.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
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
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.InMemoryWebSessionStore;
import org.springframework.web.server.session.WebSessionManager;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.logging.Logger;

/**
 * {@code SecurityConfig} defines the reactive (WebFlux) security configuration for the BFF
 * (Backend-for-Frontend) layer.
 *
 * <p>This class configures:</p>
 * <ul>
 *     <li>An isolated, dev-only filter chain for Actuator endpoints, evaluated before the main chain</li>
 *     <li>The main filter chain: authenticates requests via the session (no valid SESSION
 *         cookie mapped to a token → redirected to the OIDC provider's authorization endpoint
 *         via the OAuth2 Authorization Code flow); after a successful authorization callback,
 *         the custom {@code postLoginRedirectHandler} sends the browser back to the originally
 *         requested SPA route. Also enables OAuth2 client support (the {@code TokenRelay} filter
 *         relies on this to attach the access token to proxied requests), logout with a custom
 *         {@code ServerLogoutSuccessHandler}, and cookie-based CSRF protection</li>
 *     <li>A {@link WebFilter} that propagates the CSRF token to the frontend as a JS-readable cookie</li>
 *     <li>A {@link WebFilter} that raises session idle timeout to 12h, working around WebFlux's
 *         30-minute default so the session (and the {@code OAuth2AuthorizedClient} it holds) doesn't
 *         expire before the access token does — {@code tomcat} mode only; see inline note for the
 *         upstream issue this works around</li>
 *     <li>The in-memory {@link WebSessionManager}/{@link InMemoryWebSessionStore} pair backing
 *         sessions in {@code tomcat} mode. In {@code redis} mode these beans back off (via
 *         {@code nidam.session-mode}) and Spring Session's reactive autoconfiguration wires the
 *         Redis-backed manager instead — see {@code SessionConfig} for the serializer that makes
 *         that possible</li>
 * </ul>
 *
 * <p>Note: the OAuth2 authorized-client repository itself (what actually makes the access token
 * ride along in the session for {@code TokenRelay} to find) is declared separately in
 * {@code OAuth2ClientConfig}, deliberately unconditional on {@code nidam.session-mode} — see that
 * class for why.</p>
 */
@Configuration
public class SecurityConfig {

	private final static Logger log = Logger.getLogger(SecurityConfig.class.getName());

	private final ServerAuthenticationSuccessHandler postLoginRedirectHandler;
	private final ServerLogoutSuccessHandler logoutRedirectHandler;

	public static final String COOKIE_XSRF_TOKEN = "XSRF-TOKEN";
	public static final String BFF_LOGOUT_ENDPOINT = "/logout";

	private static final String[] UNAUTHENTICATED_PATHS = {"/api/me", "/login/**", "/oauth2/**", "/error", "/post-logout"};

	private static final String ACTUATOR_MATCHER = "/actuator/**";

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
	 * Security configuration dedicated exclusively to Actuator endpoints. Actuator is only available with {@code dev profile}.
	 *
	 * <p>This filter chain is evaluated with the highest precedence ({@code @Order(0)})
	 * and applies only to requests matching {@code /actuator/**}. It isolates Actuator
	 * from the main application security configuration to avoid unintended side effects
	 * such as CSRF enforcement, session handling, or custom filters interfering with
	 * operational endpoints.</p>
	 *
	 * <p>Key characteristics:</p>
	 * <ul>
	 *     <li><b>Scoped matching:</b> Applies only to Actuator endpoints via
	 *     {@link ServerWebExchangeMatchers#pathMatchers(String...)}.</li>
	 *     <li><b>Open access:</b> All requests are permitted. This is typically suitable
	 *     for development environments; production setups should restrict access
	 *     (e.g., to specific roles or networks).</li>
	 *     <li><b>CSRF disabled:</b> Cross-Site Request Forgery protection is turned off
	 *     since Actuator endpoints are not intended to be accessed via browser-based
	 *     sessions.</li>
	 *     <li><b>Full isolation:</b> Prevents the main security filter chain (e.g., OAuth2,
	 *     session management, custom filters) from applying to Actuator requests.</li>
	 * </ul>
	 *
	 * <p>This approach follows Spring Security best practices by defining a dedicated
	 * {@link SecurityWebFilterChain} for operational endpoints instead of relying on
	 * conditional logic within a single chain.</p>
	 *
	 * @param http the {@link ServerHttpSecurity} to configure
	 * @return a {@link SecurityWebFilterChain} that secures Actuator endpoints
	 */
	@Bean
	@Order(0)
	@Profile("dev")
	public SecurityWebFilterChain actuatorChain(ServerHttpSecurity http) {
		return http
				.securityMatcher(ServerWebExchangeMatchers.pathMatchers(ACTUATOR_MATCHER))
				.authorizeExchange(ex -> ex.anyExchange().permitAll())
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.build();
	}

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
	 *           <li>the BFF logout endpoint (POST)</li>
	 *       </ul>
	 *   </li>
	 *   <li>Requires authentication for all other requests — unauthenticated requests
	 *   (no SESSION cookie mapped to a valid access token) are redirected to the
	 * 	 OIDC provider via the OAuth2 Authorization Code flow, not handled locally</li>
	 *   <li>Runs the custom {@code postLoginRedirectHandler} after a successful authorization
	 *   callback, to send the browser back to the originally requested SPA route</li>
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
	@Order(1)
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
//					log.info("csrfTokenWebFilter() currentCookie: " + currentCookie);

					if (!token.getToken().equals(currentCookie)) {
						ResponseCookie cookie = ResponseCookie.from(COOKIE_XSRF_TOKEN, token.getToken())
								.path("/") // match the path of your app
								.sameSite("Lax")
								.httpOnly(false) // must be accessible to JS
								.secure(false) // set to true if using HTTPS
								.build();
//						log.info("csrfTokenWebFilter()  Setting new XSRF-TOKEN cookie: {} " + cookie);
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
	@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "tomcat", matchIfMissing = true)
	@Bean
	public WebFilter sessionTimeoutWebFilter() {
		return (exchange, chain) -> {
			Mono<WebSession> sessionMono = exchange.getSession()
					.filter(WebSession::isStarted)
					.doOnNext(session -> {
						if (session.getAttribute("SESSION_TIMEOUT_SET") == null) {
							session.setMaxIdleTime(Duration.ofHours(12));
							session.getAttributes().put("SESSION_TIMEOUT_SET", true);
//							log.info("Session timeout set to 12h for session ID:" + session.getId());
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

	/**
	 * Default (non-Redis) reactive session store, active when {@code nidam.session-mode} is unset
	 * or {@code tomcat}. Sessions live in process memory and don't survive a restart or scale past
	 * a single instance — this is the local-dev / pre-Redis-migration fallback, kept alongside the
	 * {@code redis} mode via {@code nidam.session-mode} rather than removed outright.
	 * <p>
	 * {@code maxSessions} is raised from the store's low built-in default (workaround, not a real
	 * capacity plan) since the in-memory store evicts oldest sessions once that cap is hit. Redis
	 * mode has no equivalent setting — expiry there is TTL-based, not a size-capped map, so this
	 * concern disappears once {@code redis} mode is used instead.
	 */
	@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "tomcat", matchIfMissing = true)
	@Bean
	public InMemoryWebSessionStore inMemoryWebSessionStore() {
		InMemoryWebSessionStore store = new InMemoryWebSessionStore();
		store.setMaxSessions(Integer.MAX_VALUE);
		return store;
	}

	/**
	 * Wires the in-memory store above into the reactive session manager. Only present in
	 * {@code tomcat} mode — Redis mode gets its {@link WebSessionManager} wired automatically by
	 * Spring Session's reactive autoconfiguration ({@code SessionDataRedisAutoConfiguration} /
	 * equivalent) once {@code spring-session-data-redis} is on the classpath and a
	 * {@code ReactiveRedisConnectionFactory} bean exists;
	 * Spring Boot resolves it via implicit bean-presence ordering.
	 * <p>
	 * Worth double-checking: that autoconfiguration backs off on
	 * {@code @ConditionalOnMissingBean(ReactiveSessionRepository.class)}, not on the presence of a
	 * {@link WebSessionManager} bean — and Spring Session's own Redis-mode configuration defines a
	 * bean literally named {@code webSessionManager}, same as this one. Since
	 * {@code spring-session-data-redis} appears to be unconditionally on the classpath here, confirm
	 * that {@code tomcat} mode doesn't also trigger Boot's Redis session autoconfiguration
	 * alongside this bean — that would be a bean name collision, not a graceful fallback.
	 */
	@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "tomcat", matchIfMissing = true)
	@Bean
	public WebSessionManager webSessionManager(InMemoryWebSessionStore store) {
		DefaultWebSessionManager manager = new DefaultWebSessionManager();
		manager.setSessionStore(store);
		return manager;
	}


}
