package com.derbyware.tokengenerator.config;

import com.derbyware.tokengenerator.repositories.UserRepository;
import com.derbyware.tokengenerator.service.JpaUserDetailsService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

@Configuration
public class SecurityConfigStaticKey {

	private Logger log = Logger.getLogger(SecurityConfigStaticKey.class.getName());

	@Bean
	@Order(1)
	public SecurityFilterChain asFilterChain(HttpSecurity http) throws Exception {
		OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

		http.getConfigurer(OAuth2AuthorizationServerConfigurer.class).oidc(Customizer.withDefaults());
		http.exceptionHandling((e) ->
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
	@Bean
	public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService) {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(userDetailsService);
		return authProvider;
	}


	@Bean
	public UserDetailsService userDetailsService(UserRepository userRepository) {
		// Custom
		UserDetailsService userDetailsService = new JpaUserDetailsService(userRepository);
		return userDetailsService;
	}

	@Bean
	public PasswordEncoder passwordEncoder(@Value("#{${custom.password.encoders}}") List<String> encoders,
	                                       @Value("#{${custom.password.idless.encoder}}") String idlessEncoderName) {
		log.info("encoders: " + encoders);

		Map<String, PasswordEncoder> encodersMapping = new HashMap<>();

		if(encoders.contains("scrypt")){ encodersMapping.put("scrypt", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8());}
		if(encoders.contains("bcrypt")){ encodersMapping.put("bcrypt", new BCryptPasswordEncoder());}
		if(encoders.contains("argon2")){ encodersMapping.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());}
		if(encoders.contains("pbkdf2")){ encodersMapping.put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());}

		// first in list used to encode
		DelegatingPasswordEncoder passwordEncoder = new DelegatingPasswordEncoder(encoders.get(0), encodersMapping);
		// use this encoder if {id} does not exist
		passwordEncoder.setDefaultPasswordEncoderForMatches(getEncoderForIdlessHash(idlessEncoderName));
		return passwordEncoder;
	}

	private PasswordEncoder getEncoderForIdlessHash(String encoderName){
		log.info("encoderName: " + encoderName);
		switch (encoderName){
			case "bcrypt":
				return new BCryptPasswordEncoder();
			case "argon2":
				return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
			case "pbkdf2":
				return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
			default:
				return SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8();
		}
	}

	// now that I use password encoders, the rules apply to the client password too. so it must be hashed with spring CLI
	// .\spring encodepassword secret
	// {bcrypt}$2a$10$.ld6BfZescPDfVVduvu.6O9.7FLMI64l4PfvnBZJQEBhTLFFbeKei
	@Bean
	public RegisteredClientRepository registeredClientRepository() {
		RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId("client")
				.clientSecret("{bcrypt}$2a$10$.ld6BfZescPDfVVduvu.6O9.7FLMI64l4PfvnBZJQEBhTLFFbeKei") //secret
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("http://localhost:7080/bff/login/oauth2/code/token-generator")
				// changed from http://localhost:4004/login/oauth2/code/token-generator
				.scope(OidcScopes.OPENID)
//				.clientSettings(ClientSettings.builder().requireProofKey(false).build())
				.tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(12)).build())
				.build();
		return new InMemoryRegisteredClientRepository(registeredClient);
	}


	// This should change to reading the private key from the resources folder
	@Bean
	public JWKSource<SecurityContext> jwkSource() throws Exception {

		KeyPair keyPair = loadKeyStore();
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
	public AuthorizationServerSettings authorizationServerSettings() {
		return AuthorizationServerSettings.builder()
//				.issuer("http://localhost:7080")
				.build();
	}

	@Value("${password}")
	private String password;

	@Value("${privateKey}")
	private String privateKey;

	@Value("${alias}")
	private String alias;

	private KeyPair loadKeyStore() throws Exception {
		final KeyStore keystore = KeyStore.getInstance("JKS");

		keystore.load(new ClassPathResource(privateKey).getInputStream(), password.toCharArray());

		final PrivateKey key = (PrivateKey) keystore.getKey(alias, password.toCharArray());
		log.info("PrivateKey key: " + key);

		final Certificate cert = keystore.getCertificate(alias);
		final PublicKey publicKey = cert.getPublicKey();
		log.info("PublicKey publicKey: " + publicKey);
		return new KeyPair(publicKey, key);

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
			log.info("Principal: " + context.getPrincipal().getAuthorities());
			List<String> auths = new ArrayList<>();
			for (GrantedAuthority auth : context.getPrincipal().getAuthorities()){
				auths.add(auth.getAuthority());
			}
			JwtClaimsSet.Builder claims = context.getClaims();
			claims.claim("authorities", auths);
		};
	}


}
