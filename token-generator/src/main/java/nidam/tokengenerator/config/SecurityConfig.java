package nidam.tokengenerator.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import nidam.tokengenerator.config.properties.ClientProperties;
import nidam.tokengenerator.config.properties.TTLProperties;
import nidam.tokengenerator.handler.OAuth2AwareFailureHandler;
import nidam.tokengenerator.handler.OAuth2AwareSuccessHandler;
import nidam.tokengenerator.redis.config.RedisOAuth2Config;
import nidam.tokengenerator.redis.config.RedisSessionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Security configuration for the token generator Authorization Server.
 *
 * <p>This class configures the three security filter chains, JWT infrastructure,
 * registered clients, and supporting beans required for the Authorization Server
 * to operate. It is the central security configuration class for the token generator,
 * applicable in both session modes ({@code tomcat} and {@code redis}).</p>
 *
 * <h3>Filter chain hierarchy</h3>
 * <table border="1">
 *     <tr>
 *         <th>Order</th>
 *         <th>Bean</th>
 *         <th>Condition</th>
 *         <th>Purpose</th>
 *     </tr>
 *     <tr>
 *         <td>{@code 0}</td>
 *         <td>{@link #actuatorChain}</td>
 *         <td>{@code dev} profile only</td>
 *         <td>Permits all requests to {@code /actuator/**} without authentication or CSRF</td>
 *     </tr>
 *     <tr>
 *         <td>{@code 1}</td>
 *         <td>{@link #asFilterChainDefault}</td>
 *         <td>{@code session-mode=tomcat} or unset</td>
 *         <td>Authorization Server endpoints with default OIDC configuration</td>
 *     </tr>
 *     <tr>
 *         <td>{@code 1}</td>
 *         <td>{@link #asFilterChainRedis}</td>
 *         <td>{@code session-mode=redis}</td>
 *         <td>Authorization Server endpoints with Redis authorization cleanup on logout</td>
 *     </tr>
 *     <tr>
 *         <td>{@code 2}</td>
 *         <td>{@link #defaultSecurityFilterChain}</td>
 *         <td>Always active</td>
 *         <td>Form login for all other requests</td>
 *     </tr>
 * </table>
 *
 * <h3>Session mode variants</h3>
 * <p>Only one of {@link #asFilterChainDefault} or {@link #asFilterChainRedis} is active
 * at a time, controlled by {@code nidam.session-mode}. The Redis variant adds an
 * {@link RedisOIDCLogoutResponseHandler} to the OIDC logout endpoint to explicitly
 * remove authorization records from Redis on logout. All other behavior is identical
 * between the two variants.</p>
 *
 * <h3>JWT signing key stability</h3>
 * <p>Uses {@link JWKKeyManager} to load or generate an RSA key pair persisted at
 * {@code key/jwk.json}. Both token generator instances in a high availability deployment
 * must share this file to ensure tokens signed by one instance can be verified by the
 * other. See {@link JWKKeyManager} for details.</p>
 *
 * @see RedisOAuth2Config
 * @see RedisSessionConfig
 * @see RedisOIDCLogoutResponseHandler
 * @see JWKKeyManager
 */
@Configuration
public class SecurityConfig {

	private final Logger log = Logger.getLogger(SecurityConfig.class.getName());

	@Value("${issuer}")
	private String issuer;

	public static final String LOGIN_ENDPOINT = "/login";
	public static final String JWT_CLAIM_TOKEN = "authorities";

	private final ClientProperties clientProperties;

	private static final String[] ALLOWED_PATHS = {"/css/**", "/media/**", "/vendors/**", "/error"};
	private static final String ACTUATOR_MATCHER = "/actuator/**";

	public SecurityConfig(ClientProperties clientProperties) {
		this.clientProperties = clientProperties;
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
	 * @param http the {@link ServerHttpSecurity} to configure
	 * @return a {@link SecurityWebFilterChain} that secures Actuator endpoints
	 */
	@Bean
	@Order(0)
	@Profile("dev")
	public SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher(ACTUATOR_MATCHER)
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.csrf(csrf -> csrf.disable())
				.build();
	}

	/**
	 * Creates the Authorization Server security filter chain for in-memory (Tomcat) session mode.
	 * <p>
	 * Active when {@code nidam.session-mode} is set to {@code tomcat} or is not specified.
	 * Configures the standard Authorization Server endpoints with full OpenID Connect support
	 * using Spring's default OIDC configuration. All requests require authentication and
	 * unauthenticated requests are redirected to the login endpoint.
	 *
	 * @param http the {@link HttpSecurity} to configure
	 * @return the configured {@link SecurityFilterChain}
	 * @throws Exception if an error occurs configuring the filter chain
	 */
	@Bean
	@Order(1)
	@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "tomcat", matchIfMissing = true)
	public SecurityFilterChain asFilterChainDefault(HttpSecurity http) throws Exception {
		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

		http
				.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
				.with(authorizationServerConfigurer, (authorizationServer) -> authorizationServer.oidc(Customizer.withDefaults()))
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.exceptionHandling((e) ->
						e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(LOGIN_ENDPOINT))
				);
		return http.build();
	}

	/**
	 * Creates the Authorization Server security filter chain for Redis session mode.
	 * <p>
	 * Active only when {@code nidam.session-mode} is set to {@code redis}. Functionally
	 * equivalent to {@link #asFilterChainDefault} with one addition: the OIDC logout endpoint
	 * is configured with a {@link RedisOIDCLogoutResponseHandler} that explicitly removes the
	 * {@link OAuth2Authorization} record from Redis before delegating to the default logout
	 * behavior. All other logout handling — session invalidation and
	 * {@code post_logout_redirect_uri} redirect — proceeds exactly as in the default chain.
	 * <p>
	 * This addition is necessary because Spring Authorization Server does not call
	 * {@link OAuth2AuthorizationService#remove} during standard OIDC RP-initiated logout,
	 * leaving stale authorization records in Redis until their TTL expires.
	 *
	 * @param http                           the {@link HttpSecurity} to configure
	 * @param redisOIDCLogoutResponseHandler the handler that removes the authorization record
	 *                                       from Redis and delegates to the default logout behavior
	 * @return the configured {@link SecurityFilterChain}
	 * @throws Exception if an error occurs configuring the filter chain
	 */
	@Bean
	@Order(1)
	@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "redis")
	public SecurityFilterChain asFilterChainRedis(HttpSecurity http,
	                                              RedisOIDCLogoutResponseHandler redisOIDCLogoutResponseHandler) throws Exception {
		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

		http
				.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
				.with(authorizationServerConfigurer, authorizationServer ->
						authorizationServer.oidc(oidc ->
								oidc.logoutEndpoint(logout ->
										logout.logoutResponseHandler(redisOIDCLogoutResponseHandler))
						)
				)
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
				.exceptionHandling((e) ->
						e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(LOGIN_ENDPOINT))
				);
		return http.build();
	}

	/**
	 * Defines the default security filter chain for the application.
	 * <p>
	 * Configures form-based login with custom success and failure handlers,
	 * * and enforces authentication for all incoming HTTP requests except static resources and the login page.
	 * <p>
	 * Assets required for rendering the custom login page are publicly accessible.
	 * All other requests require authentication.
	 *
	 * @param http           the {@link HttpSecurity} to configure
	 * @param successHandler the {@link OAuth2AwareSuccessHandler} used after successful login
	 * @param failureHandler the {@link OAuth2AwareFailureHandler} used for login failures
	 * @return the configured {@link SecurityFilterChain}
	 * @throws Exception if an error occurs while building the filter chain
	 */
	@Bean
	@Order(2)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, OAuth2AwareSuccessHandler successHandler,
	                                                      OAuth2AwareFailureHandler failureHandler) throws Exception {
		http.formLogin(formLogin -> formLogin
				.loginPage(LOGIN_ENDPOINT)
				.successHandler(successHandler)
				.failureHandler(failureHandler)
				.permitAll());
		http.authorizeHttpRequests(c -> c
				.requestMatchers(ALLOWED_PATHS).permitAll()
				.anyRequest().authenticated());
		return http.build();
	}

	// now that I use password encoders, the rules apply to the client password too. so it must be hashed with spring CLI
	// .\spring encodepassword secret
	// {bcrypt}$2a$10$.ld6BfZescPDfVVduvu.6O9.7FLMI64l4PfvnBZJQEBhTLFFbeKei

	/**
	 * Registers a single OAuth2 client in-memory using client properties defined in the application configuration.
	 * <p>
	 * The client secret must be hashed using Spring's PasswordEncoder.
	 *
	 * @return a {@link RegisteredClientRepository} containing the configured client
	 */
	@Bean
	public RegisteredClientRepository registeredClientRepository(TTLProperties ttlProperties) {
		RegisteredClient registeredClient = RegisteredClient.withId(clientProperties.getInternalIdentifier())
				.clientId(clientProperties.getId())
				.clientSecret(clientProperties.getSecretHash()) //secret
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.redirectUri(clientProperties.getLoginUri())    // changed from http://localhost:4004/login/oauth2/code/token-generator
				.scope(OidcScopes.OPENID)
				.postLogoutRedirectUri(clientProperties.getBffPostLogoutUri())            //.postLogoutRedirectUri("http://localhost:7080/bff/post-logout")
				.tokenSettings(TokenSettings.builder()
						.accessTokenTimeToLive(ttlProperties.getAccessToken())
						.refreshTokenTimeToLive(ttlProperties.getRefreshToken())
						.authorizationCodeTimeToLive(ttlProperties.getAuthCode())
						.reuseRefreshTokens(false)
						.build())
				.build();
		return new InMemoryRegisteredClientRepository(registeredClient);
	}

	/**
	 * Provides the application's JSON Web Key (JWK) source used for signing
	 * and verifying OAuth2 / OpenID Connect tokens.
	 *
	 * <p>This method relies on {@link JWKKeyManager} to load an existing
	 * RSA key from the filesystem or generate and persist a new one if none exists.
	 * The key is then exposed as an immutable {@link JWKSet}.</p>
	 *
	 * <p>The resulting {@link JWKSource} is used internally by Spring Authorization Server
	 * to:
	 * <ul>
	 *     <li>Sign ID tokens and access tokens</li>
	 *     <li>Expose the public key via the JWK Set endpoint ({@code /oauth2/jwks})</li>
	 * </ul>
	 *
	 * <p>The underlying key material is persisted in {@code key/jwk.json}, ensuring
	 * stability across application restarts.</p>
	 *
	 * @return a {@link JWKSource} backed by a single RSA key
	 */
	@Bean
	public JWKSource<SecurityContext> jwkSource() {
		RSAKey rsaKey = JWKKeyManager.loadOrCreate();
		JWKSet jwkSet = new JWKSet(rsaKey);
		return new ImmutableJWKSet<>(jwkSet);
	}

	/**
	 * Creates a {@link JwtDecoder} from the provided JWK source.
	 *
	 * @param jwkSource the source of JWKs
	 * @return a {@link JwtDecoder}
	 */
	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}

	/**
	 * Provides the settings for the Authorization Server, including the issuer URL.
	 *
	 * @return the {@link AuthorizationServerSettings}
	 */
	@Bean
	public AuthorizationServerSettings authorizationServerSettings() {
		return AuthorizationServerSettings.builder().issuer(issuer).build();
//		issuer must always be explicitly set, reasons: 1.Move between environments (dev, staging, prod).
//		2.Generate tokens in code outside HTTP request processing.
	}


	// this adds custom info to the token payload
//	 "authorities": [
//			 "manage-users",
//			 "manage-projects"
//			 ]

	/**
	 * Customizes the JWT access token by adding a claim containing the user's granted authorities.
	 * <p>
	 * Only applies customization to access tokens (not ID tokens).
	 *
	 * @return an {@link OAuth2TokenCustomizer} for {@link JwtEncodingContext}
	 */
	@Bean
	public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer(TTLProperties ttlProperties) {
		return context -> {
//			log.info("grant: " + context.getAuthorizationGrant().getAuthorities());
//			log.info("Authorization: " + context.getAuthorization().getAttributes());

//			Run twice for some reason: fix by gpt to test. works. it says because:
//			One invocation during access token generation.
//			Another during ID token (OIDC) generation.
//			✅ Solution: Filter by token type:

			if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
				log.info("Principal: " + context.getPrincipal().getAuthorities());
				List<String> auths = new ArrayList<>();
				for (GrantedAuthority auth : context.getPrincipal().getAuthorities()) {
					auths.add(auth.getAuthority());
				}
				JwtClaimsSet.Builder claims = context.getClaims();
				claims.claim(JWT_CLAIM_TOKEN, auths);
				claims.claim(StandardClaimNames.EMAIL, context.getPrincipal().getName());
			}

			// change default 30 minutes to use the same value of ttl id token
			if ("id_token".equals(context.getTokenType().getValue())) {
				context.getClaims().expiresAt(context.getClaims().build().getIssuedAt().plus(ttlProperties.getIdToken()));
			}
		};
	}

//	from gpt: Spring Boot should automatically honor X-Forwarded-* if:
//	server.forward-headers-strategy=framework
//	... but in some cases (especially with Spring Security + Gateway), it’s necessary to explicitly register the filter:
//	the problem this fix is that after setting the reverse proxy with:
//		filters:
//  		- PreserveHostHeader
//          - AddRequestHeader=X-Forwarded-Proto, http
//	the authorization server redirects to http://localhost/auth/login instead of http://localhost:7080/auth/login

	/**
	 * Registers a {@link ForwardedHeaderFilter} to correctly handle {@code X-Forwarded-*} headers when
	 * the application is behind a reverse proxy (e.g., Spring Cloud Gateway).
	 *
	 * @return a {@link FilterRegistrationBean} for {@link ForwardedHeaderFilter}
	 */
	@Bean
	public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
		FilterRegistrationBean<ForwardedHeaderFilter> filter = new FilterRegistrationBean<>();
		filter.setFilter(new ForwardedHeaderFilter());
		return filter;
	}

}
