package nidam.tokengenerator.config;

import nidam.tokengenerator.repositories.UserRepository;
import nidam.tokengenerator.service.JpaUserDetailsService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
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
import java.util.*;
import java.util.logging.Logger;

@Configuration
public class SecurityConfigStaticKey {

	private Logger log = Logger.getLogger(SecurityConfigStaticKey.class.getName());

	@Value("${password}")
	private String password;

	@Value("${privateKey}")
	private String privateKey;

	@Value("${alias}")
	private String alias;

	@Bean
	@Order(1)
	public SecurityFilterChain asFilterChain(HttpSecurity http) throws Exception {
		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

		http
				.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
				.with(authorizationServerConfigurer, (authorizationServer) -> authorizationServer.oidc(Customizer.withDefaults()))
				.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())

//		old from boot 3.2
//		OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
//		http.getConfigurer(OAuth2AuthorizationServerConfigurer.class).oidc(Customizer.withDefaults());

				.exceptionHandling((e) ->
						e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
				);
		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http.formLogin(Customizer.withDefaults());
		http.authorizeHttpRequests(c -> c.anyRequest().authenticated());
		return http.build();
	}

	// This bean should be added because of a bug in boot:
	// https://stackoverflow.com/questions/77686158/spring-authorization-server-not-working-after-boot-3-2-upgrade
	// https://github.com/spring-projects/spring-authorization-server/issues/1475
//	@Bean
//	public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService) {
//		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
//		authProvider.setUserDetailsService(userDetailsService);
//		return authProvider;
//	}
//
//
//	@Bean
//	public UserDetailsService userDetailsService(UserRepository userRepository) {
//		// Custom
//		UserDetailsService userDetailsService = new JpaUserDetailsService(userRepository);
//		return userDetailsService;
//	}

	// now that I use password encoders, the rules apply to the client password too. so it must be hashed with spring CLI
	// .\spring encodepassword secret
	// {bcrypt}$2a$10$.ld6BfZescPDfVVduvu.6O9.7FLMI64l4PfvnBZJQEBhTLFFbeKei
	@Bean
	public RegisteredClientRepository registeredClientRepository() {
		// TODO use application.properties to inject values instead of hard coding
		RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId("client")
				.clientSecret("{bcrypt}$2a$10$.ld6BfZescPDfVVduvu.6O9.7FLMI64l4PfvnBZJQEBhTLFFbeKei") //secret
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("http://localhost:7080/bff/login/oauth2/code/token-generator")
				// changed from http://localhost:4004/login/oauth2/code/token-generator
				.scope(OidcScopes.OPENID)
//				.clientSettings(ClientSettings.builder().requireProofKey(false).build())
				.postLogoutRedirectUri("http://localhost:7080/react-ui")
				.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(12)).build())
				.build();
		return new InMemoryRegisteredClientRepository(registeredClient);
	}

	@Bean
	public JWKSource<SecurityContext> jwkSource() throws Exception {

		KeyPair keyPair = JKSFileKeyPairLoader.loadKeyStore(privateKey, password, alias);
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

		RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(UUID.randomUUID().toString())
				.build();
		JWKSet jwkSet = new JWKSet(rsaKey);
		return new ImmutableJWKSet<>(jwkSet);
	}

	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}

	@Bean
	public AuthorizationServerSettings authorizationServerSettings() {
		return AuthorizationServerSettings.builder()
				.issuer("http://localhost:7080/auth")
				.build();
//		issuer must always be explicitly set, reasons: 1.Move between environments (dev, staging, prod).
//		2.Generate tokens in code outside HTTP request processing.
	}


	// this adds custom info to the token payload
//	 "authorities": [
//			 "manage-users",
//			 "manage-projects"
//			 ]
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
				claims.claim("authorities", auths);
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
	@Bean
	public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
		FilterRegistrationBean<ForwardedHeaderFilter> filter = new FilterRegistrationBean<>();
		filter.setFilter(new ForwardedHeaderFilter());
		return filter;
	}

}
