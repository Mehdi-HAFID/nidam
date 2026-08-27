package nidam.registration.config;

import jakarta.servlet.DispatcherType;
import nidam.registration.config.properties.AllowedCorsUriProperties;
import nidam.registration.config.properties.PasswordProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Configuration
@EnableFeignClients(basePackages = "nidam.registration.proxy")
public class ProjectConfig {

	private static final String REGISTER_ENDPOINT = "/register";
	private static final String REGISTER_RECAPTCHA_ENDPOINT = "/registerCaptcha";
	private final Logger log = Logger.getLogger(ProjectConfig.class.getName());

	private static final String ACTUATOR_MATCHER = "/actuator/**";

	/**
	 * Security configuration dedicated exclusively to Actuator endpoints. Actuator is only available with {@code dev and prod profiles}.
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
	@Profile({"dev", "prod"})
	public SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher(ACTUATOR_MATCHER)
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.csrf(csrf -> csrf.disable())
				.build();
	}

	/**
	 * Configures the Spring Security filter chain for the registration microservice.
	 * <p>
	 * This configuration is tailored for a service that exposes public registration endpoints
	 * which are called directly from a frontend Single Page Application (SPA) running in the browser.
	 * Since the SPA is served on a different origin (e.g. <code>http://localhost:4001</code>),
	 * CORS rules must be explicitly configured here.
	 * </p>
	 *
	 * <h2>Authorization rules</h2>
	 * <ul>
	 *   <li><b>Permit all</b> requests dispatched with {@link jakarta.servlet.DispatcherType#ERROR} –
	 *       allows Spring's error handling to work without security restrictions.</li>
	 *   <li><b>Permit all</b> for {@code POST /register} and {@code POST /registerCaptcha} –
	 *       these endpoints must be publicly accessible for new users to sign up.</li>
	 *   <li><b>Deny all</b> other requests – this microservice is not meant to serve
	 *       authenticated or arbitrary endpoints.</li>
	 * </ul>
	 *
	 * <h2>CSRF protection</h2>
	 * <ul>
	 *   <li>CSRF protection is <b>disabled</b> using {@link org.springframework.security.config.annotation.web.configurers.CsrfConfigurer#disable()}.</li>
	 *   <li>Reason: requests come from a public SPA with no session-based authentication,
	 *       so CSRF protection is unnecessary here.</li>
	 * </ul>
	 *
	 * <h2>HTTP Basic authentication</h2>
	 * <ul>
	 *   <li>Explicitly <b>disabled</b> via {@link org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer#disable()}.</li>
	 *   <li>Reason: the registration API is not protected by username/password authentication,
	 *       only by the allowed endpoints and CORS restrictions.</li>
	 * </ul>
	 *
	 * <h2>CORS configuration</h2>
	 * <ul>
	 *   <li>Enables CORS support with a custom {@link org.springframework.web.cors.CorsConfigurationSource}.</li>
	 *   <li>Allowed origins: <code>http://localhost:4001</code>, <code>http://127.0.0.1:4001</code>,
	 *       <code>http://localhost:7080</code>, <code>http://127.0.0.1:7080</code>.</li>
	 *   <li>Allowed methods: all (<code>*</code>).</li>
	 *   <li>Allowed headers: all (<code>*</code>).</li>
	 *   <li>This ensures that the SPA, even when hosted on a different port, can send requests
	 *       to the registration service without being blocked by the browser's same-origin policy.</li>
	 * </ul>
	 *
	 * @param http the {@link HttpSecurity} to modify
	 * @return a configured {@link SecurityFilterChain} bean
	 * @throws Exception if an error occurs while building the security configuration
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain filterChain(HttpSecurity http, AllowedCorsUriProperties corsUris) throws Exception {
		http.authorizeHttpRequests((authorizeHttpRequests) ->
						authorizeHttpRequests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll().
								requestMatchers(HttpMethod.POST, REGISTER_ENDPOINT, REGISTER_RECAPTCHA_ENDPOINT).permitAll().
//								requestMatchers("/actuator/**").permitAll().
								anyRequest().denyAll()
				);
		http.csrf((csrf) -> csrf.disable());
		http.httpBasic(httpSecurityHttpBasicConfigurer -> httpSecurityHttpBasicConfigurer.disable());
		http.cors(c -> {
			CorsConfigurationSource source = request -> {
				CorsConfiguration config = new CorsConfiguration();
				config.setAllowedOrigins(corsUris.getAllowedCorsUri());
				config.setAllowedMethods(List.of("*"));
				config.setAllowedHeaders(List.of("*"));
				return config;
			};
			c.configurationSource(source);
		});
		return http.build();
	}

	/**
	 * Builds the {@link PasswordEncoder} bean from the {@code password-encoders} /
	 * {@code password-idless} properties instead of hardcoding an algorithm, so the
	 * active hashing scheme (and which legacy schemes remain verifiable) is a config
	 * change, not a redeploy.
	 * <p>
	 * The first entry in {@code password-encoders} is the encoder used for new hashes
	 * (via {@link DelegatingPasswordEncoder}'s {@code idForEncode}); the rest are kept
	 * only so existing hashes under those ids can still be matched.
	 * <p>
	 * {@code password-idless} sets the fallback encoder for hashes with no {@code {id}}
	 * prefix, via {@link DelegatingPasswordEncoder#setDefaultPasswordEncoderForMatches},
	 * to support verifying pre-migration hashes that predate the {id} format.
	 *
	 *  @throws IllegalArgumentException if {@code password-encoders} is empty, contains
	 *                                   an unsupported algorithm name, or if
	 *                                   {@code password-idless} is missing/blank or
	 *                                   names an unsupported algorithm
	 */
	@Bean
	public PasswordEncoder passwordEncoder(PasswordProperties passwordProperties) {
//		List<String> encoders = passwordProperties.getEncoders();
		List<String> encoders = new ArrayList<>(passwordProperties.getEncoders());
		log.info("encoders: " + encoders);

		Map<String, Supplier<PasswordEncoder>> encoderSuppliers = Map.of(
				"bcrypt", () -> new BCryptPasswordEncoder(),
				"argon2", () -> Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"pbkdf2", () -> Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"scrypt", () -> SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8()
		);

		if (encoders.isEmpty()) {
			throw new IllegalArgumentException("password-encoders must contain at least one password encoder");
		}

		List<String> unsupportedEncoders = encoders.stream().filter(encoder -> !encoderSuppliers.containsKey(encoder)).toList();

		if (!unsupportedEncoders.isEmpty()) {
			throw new IllegalArgumentException("Unsupported password encoder(s): " + unsupportedEncoders);
		}

		Map<String, PasswordEncoder> encodersMapping = encoders.stream()
				.filter(key1 -> encoderSuppliers.containsKey(key1))
				.collect(Collectors.toMap(Function.identity(), key -> encoderSuppliers.get(key).get()));


		// first in list used to encode
		DelegatingPasswordEncoder passwordEncoder = new DelegatingPasswordEncoder(encoders.getFirst(), encodersMapping);

		// use this encoder if {id} does not exist
		String idlessEncoder = passwordProperties.getIdless();
		if (idlessEncoder == null || idlessEncoder.trim().isEmpty()) {
			throw new IllegalArgumentException("password-idless must specify a password encoder");
		}

		Supplier<PasswordEncoder> idlessEncoderSupplier = encoderSuppliers.get(idlessEncoder);
		if (idlessEncoderSupplier == null) {
			throw new IllegalArgumentException("Unsupported password-idless encoder: " + idlessEncoder);
		}

		passwordEncoder.setDefaultPasswordEncoderForMatches(idlessEncoderSupplier.get());
		return passwordEncoder;
	}

}
