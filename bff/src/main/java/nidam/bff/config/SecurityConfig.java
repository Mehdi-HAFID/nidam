package nidam.bff.config;

import nidam.bff.security.IdTokenSyncingRefreshTokenAuthorizedClientProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
import org.springframework.web.server.ServerWebExchange;
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

	private static final String[] UNAUTHENTICATED_PATHS = {"/api/me", "/login/**", "/oauth2/**", "/error"};

	@Value("${bff-post-logout-endpoint}")
	private String postLogoutEndpoint;

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
	@Profile({"dev", "prod"})
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
						.pathMatchers(postLogoutEndpoint).permitAll()
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
	  WebFilter that sets session max idle time to 7 days.
	  submitted an issue https://github.com/spring-projects/spring-framework/issues/35240
	 */

	/**
	 * Configures a {@link WebFilter} that sets a maximum idle timeout of 7 days on each WebFlux session,
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
	public WebFilter sessionTimeoutWebFilter(@Value("${spring.session.timeout}") Duration ttlTomcatSession) {
		return (exchange, chain) -> {
			Mono<WebSession> sessionMono = exchange.getSession()
					.filter(WebSession::isStarted)
					.doOnNext(session -> {
						if (session.getAttribute("SESSION_TIMEOUT_SET") == null) {
							session.setMaxIdleTime(ttlTomcatSession);
							session.getAttributes().put("SESSION_TIMEOUT_SET", true);
//							log.info("Session timeout set to 12h for session ID:" + session.getId());
//							log.info("Max idle time: " + session.getMaxIdleTime());
//							log.info("Creation time: " + session.getCreationTime());
						}

					});
			return sessionMono.then(chain.filter(exchange));
		};
	}

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

	/**
	 * Supplies the {@link ReactiveOAuth2AuthorizedClientManager} that
	 * {@code TokenRelayGatewayFilterFactory} uses to authorize and refresh clients for
	 * every proxied request.
	 *
	 * <h2>Why this bean has to exist at all</h2>
	 * <p>Without it, {@code ReactiveOAuth2ClientConfiguration.ReactiveOAuth2AuthorizedClientManagerRegistrar}
	 * auto-creates a default manager (visible in trace logs as the singleton bean
	 * {@code 'authorizedClientManager'} or similar, resolved via
	 * {@code @ConditionalOnMissingBean(ReactiveOAuth2AuthorizedClientManager.class)}) whose
	 * provider chain includes the stock {@code RefreshTokenReactiveOAuth2AuthorizedClientProvider}.
	 * That provider refreshes the access/refresh token pair but never touches the
	 * {@code SecurityContext} — see {@link IdTokenSyncingRefreshTokenAuthorizedClientProvider}'s
	 * class-level Javadoc for the full failure mode this causes at logout. Declaring our
	 * own bean of this exact type makes the registrar back off entirely (type-based
	 * conditional, not name-based), handing {@code TokenRelayGatewayFilterFactory} — via
	 * {@code .oauth2Client(Customizer.withDefaults())} in the security config, which looks
	 * the manager up from the application context rather than building its own — this bean
	 * instead.</p>
	 *
	 * <h2>How the pieces fit together</h2>
	 * <ol>
	 *   <li>{@code .authorizationCode()} handles the initial code-exchange grant type,
	 *   unchanged from Spring's default — this bean only customizes the refresh path.</li>
	 *   <li>{@code .provider(new IdTokenSyncingRefreshTokenAuthorizedClientProvider(...))}
	 *   replaces (not supplements) the stock refresh provider. It must be the only
	 *   refresh-capable provider in this chain: {@code DelegatingReactiveOAuth2AuthorizedClientProvider}
	 *   stops at the first provider returning a non-empty {@code Mono}, so having both
	 *   would risk one silently shadowing the other depending on order.</li>
	 *   <li>The freshly-constructed {@link WebSessionServerSecurityContextRepository}
	 *   handed to the provider is safe as a standalone instance rather than an injected
	 *   bean: the class is stateless, reading/writing the same well-known
	 *   {@code "SPRING_SECURITY_CONTEXT"} WebSession attribute regardless of which
	 *   instance touches it — including the one {@code ServerHttpSecurity} builds
	 *   internally by default when {@link org.springframework.security.config.web.server.ServerHttpSecurity#securityContextRepository}
	 *   is never explicitly called, which is the case in this app's security config.</li>
	 *   <li>{@code authorizedClientRepository} is expected to resolve to this app's
	 *   backed {@code WebSessionServerOAuth2AuthorizedClientRepository} bean (see
	 *   {@code OAuth2ClientConfig}) by type — required so refreshed access/refresh tokens
	 *   actually persist to the session rather than the framework's disconnected
	 *   in-memory default.</li>
	 * </ol>
	 *
	 * <p><strong>Depends on {@link #serverWebExchangeContextFilter()} being registered</strong> —
	 * the provider's ID-token sync step needs a {@link ServerWebExchange} that this bean
	 * has no way to supply directly; see that method's Javadoc for why.</p>
	 *
	 * @param clientRegistrationRepository source of registered OAuth2/OIDC client metadata
	 *                                     (issuer, token endpoint, client id, etc.)
	 * @param authorizedClientRepository   where authorized clients (access + refresh
	 *                                     tokens) are persisted between requests
	 * @return a manager wired with the authorization_code provider plus our ID-token-syncing
	 * replacement for the refresh_token provider
	 */
	@Bean
	public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(ReactiveClientRegistrationRepository clientRegistrationRepository,
			ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {

		// Freshly instantiated rather than autowired - see Javadoc above for why that's safe here.
		ServerSecurityContextRepository securityContextRepository = new WebSessionServerSecurityContextRepository();

		// Compose the provider chain: default authorization_code handling, custom refresh handling.
		ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
				.authorizationCode()
				.provider(new IdTokenSyncingRefreshTokenAuthorizedClientProvider(securityContextRepository))
				.build();

		// The concrete manager type TokenRelay/WebClient's OAuth2 support expects; same class the
		// framework's own auto-configured bean would have used, just with our provider chain instead.
		DefaultReactiveOAuth2AuthorizedClientManager manager = new DefaultReactiveOAuth2AuthorizedClientManager(
				clientRegistrationRepository, authorizedClientRepository);

		// Swap in our chain in place of whatever DefaultReactiveOAuth2AuthorizedClientManager's own
		// constructor would have defaulted to.
		manager.setAuthorizedClientProvider(authorizedClientProvider);
		return manager;
//		return new SessionCoalescingAuthorizedClientManager(manager); this how SessionCoalescingAuthorizedClientManager is set to be used
	}

	/**
	 * Publishes the current {@link ServerWebExchange} into Reactor {@code Context} for
	 * every request, so reactive code with no direct parameter access to the exchange —
	 * specifically, {@link IdTokenSyncingRefreshTokenAuthorizedClientProvider}, invoked
	 * deep inside {@code TokenRelayGatewayFilterFactory}'s reactive call graph — can still
	 * retrieve it.
	 *
	 * <h2>Why this is necessary</h2>
	 * <p>{@code ServerOAuth2AuthorizedClientExchangeFilterFunction} (used by plain
	 * {@code WebClient} calls) threads the {@link ServerWebExchange} through as an
	 * {@code OAuth2AuthorizeRequest} attribute, making it available via
	 * {@code OAuth2AuthorizationContext.getAttribute(ServerWebExchange.class.getName())}.
	 * {@code TokenRelayGatewayFilterFactory} does not do this — confirmed empirically via
	 * trace logging, not assumed from documentation — so that attribute lookup returns
	 * {@code null} for every refresh triggered through the gateway's token relay path.
	 * {@link ServerWebExchangeContextFilter} (standard Spring Framework since 5.2,
	 * originally proposed specifically to benefit Spring Security's OAuth2 support — see
	 * spring-projects/spring-framework#21746) is the general-purpose alternative: it
	 * writes the exchange into Reactor {@code Context} rather than a request attribute,
	 * which propagates correctly across the thread hops in TokenRelay's reactive
	 * pipeline regardless of who's calling.</p>
	 *
	 * <h2>Why {@code @Order} doesn't actually matter here</h2>
	 * <p>Confirmed empirically: this works identically at {@code Ordered.LOWEST_PRECEDENCE}
	 * as it does at {@code HIGHEST_PRECEDENCE}. Every {@link WebFilter} — regardless of its
	 * configured order — ultimately delegates to the same terminal destination (routing,
	 * then {@code TokenRelayGatewayFilterFactory}, then this provider) via its own
	 * {@code chain.filter(exchange)} call, and that terminal destination is only ever
	 * reached after every WebFilter, in whatever order, has done its part. Wrapping
	 * <em>this</em> filter's own {@code chain.filter(exchange)} call in
	 * {@code contextWrite(...)} makes the context visible to everything reached from that
	 * call onward — which always includes the terminal routing/dispatch, irrespective of
	 * this filter's position relative to its peers. What would actually break propagation
	 * is something between this filter and dispatch detaching into a separate subscription
	 * instead of composing normally (flatMap/then/etc.) — not order.</p>
	 *
	 * <p>That Reactor Context propagation actually works end-to-end in this exact
	 * application is not theoretical: {@code ReactiveSecurityContextHolder}'s read of the
	 * {@code SecurityContext} inside {@code AuthenticatedReactiveAuthorizationManager} —
	 * visible in this app's own trace logs on every authenticated request — relies on the
	 * identical mechanism, just for a different context value. This filter adds one more
	 * entry alongside it; the two coexist without conflict since Reactor Context entries
	 * are keyed independently.</p>
	 *
	 * @return a {@link ServerWebExchangeContextFilter} instance
	 */
	@Bean
	public ServerWebExchangeContextFilter serverWebExchangeContextFilter() {
		return new ServerWebExchangeContextFilter();
	}

}
