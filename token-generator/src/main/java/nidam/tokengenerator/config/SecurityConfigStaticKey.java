package nidam.tokengenerator.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import nidam.tokengenerator.config.properties.ClientProperties;
import nidam.tokengenerator.config.properties.KeystoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Security configuration for the Authorization Server using a static RSA key loaded from a JKS keystore.
 * <p>
 * This class sets up the necessary security filter chains, JWT decoder, token customizer,
 * registered clients, and other security-related beans for OAuth2 Authorization Server.
 * </p>
 */
@Configuration
public class SecurityConfigStaticKey {

	private final Logger log = Logger.getLogger(SecurityConfigStaticKey.class.getName());

	@Value("${issuer}")
	private String issuer;

	public static final String LOGIN_ENDPOINT = "/login";
	public static final String JWT_CLAIM_TOKEN = "authorities";

	private final KeystoreProperties keystoreProperties;
	private final ClientProperties clientProperties;

	SecurityConfigStaticKey(KeystoreProperties keystoreProperties, ClientProperties clientProperties) {
		this.keystoreProperties = keystoreProperties;
		this.clientProperties = clientProperties;
	}

	/**
	 * Creates the Authorization Server security filter chain.
	 * <p>
	 * This filter chain configures endpoints provided by the Authorization Server and enables OpenID Connect support.
	 * All requests require authentication and unauthenticated requests are redirected to the login endpoint.
	 *
	 * @param http the {@link HttpSecurity} to modify
	 * @return the configured {@link SecurityFilterChain}
	 * @throws Exception if an error occurs configuring the filter chain
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain asFilterChain(HttpSecurity http) throws Exception {
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
	 * Creates the default security filter chain for the application.
	 * <p>
	 * Configures form login and requires authentication for all HTTP requests.
	 *
	 * @param http the {@link HttpSecurity} to modify
	 * @return the configured {@link SecurityFilterChain}
	 * @throws Exception if an error occurs configuring the filter chain
	 */
	@Bean
	@Order(2)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http.formLogin(Customizer.withDefaults());
		http.authorizeHttpRequests(c -> c.anyRequest().authenticated());
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
	public RegisteredClientRepository registeredClientRepository() {
		RegisteredClient registeredClient = RegisteredClient.withId(clientProperties.getInternalIdentifier())
				.clientId(clientProperties.getId())
				.clientSecret(clientProperties.getSecretHash()) //secret
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
//				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)	waiting for spring auth server logout bug to be fixed
				.redirectUri(clientProperties.getLoginUri())	// changed from http://localhost:4004/login/oauth2/code/token-generator
				.scope(OidcScopes.OPENID)
				.postLogoutRedirectUri(clientProperties.getLogoutUri())			//.postLogoutRedirectUri("http://localhost:7080/react-ui")
				.tokenSettings(TokenSettings.builder()
						.accessTokenTimeToLive(Duration.ofHours(12))
//						.refreshTokenTimeToLive(Duration.ofHours(24))  waiting for spring auth server logout bug to be fixed
//						.reuseRefreshTokens(false)
						.build())
				.build();
		return new InMemoryRegisteredClientRepository(registeredClient);
	}

	/**
	 * Loads an RSA key pair from a JKS keystore and exposes it as a {@link JWKSource}.
	 *
	 * @return an {@link ImmutableJWKSet} containing the RSA key
	 * @throws Exception if the key pair cannot be loaded
	 */
	@Bean
	public JWKSource<SecurityContext> jwkSource() throws Exception {

		KeyPair keyPair = JKSFileKeyPairLoader.loadKeyStore(keystoreProperties.getPrivateKey(), keystoreProperties.getPassword(), keystoreProperties.getAlias());
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

		RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(UUID.randomUUID().toString())
				.build();
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
	public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
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
				for (GrantedAuthority auth : context.getPrincipal().getAuthorities()){
					auths.add(auth.getAuthority());
				}
				JwtClaimsSet.Builder claims = context.getClaims();
				claims.claim(JWT_CLAIM_TOKEN, auths);
				claims.claim(StandardClaimNames.EMAIL, context.getPrincipal().getName());
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
