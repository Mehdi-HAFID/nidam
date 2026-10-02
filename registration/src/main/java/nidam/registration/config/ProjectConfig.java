package nidam.registration.config;

import jakarta.servlet.DispatcherType;
import nidam.registration.config.properties.PasswordProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
	 * @param http the {@link HttpSecurity} to configure
	 * @return a {@link SecurityFilterChain} that secures Actuator endpoints
	 * @throws Exception if the security configuration cannot be built
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
	 * Configures the Spring Security filter chain for the registration service.
	 *
	 * <p>The registration service exposes only the endpoints required to create
	 * a new user account. Browser-facing requests are served through the Nidam
	 * reverse proxy, so cross-origin access is handled at the proxy level rather
	 * than by this service. This security configuration therefore contains no
	 * CORS rules.</p>
	 *
	 * <h2>Authorization rules</h2>
	 * <ul>
	 *   <li><b>Permit all</b> requests dispatched with
	 *       {@link DispatcherType#ERROR} so that Spring's error handling can
	 *       complete without additional security restrictions.</li>
	 *   <li><b>Permit all</b> {@code POST /register} and
	 *       {@code POST /registerCaptcha}, which are the public endpoints used
	 *       for user registration.</li>
	 *   <li><b>Deny all</b> other requests. The registration service is not
	 *       intended to expose any additional endpoints.</li>
	 * </ul>
	 *
	 * <h2>CSRF protection</h2>
	 * <p>CSRF protection is disabled because the registration endpoints are
	 * public and do not rely on an authenticated session or other browser
	 * authentication state.</p>
	 *
	 * <h2>HTTP Basic authentication</h2>
	 * <p>HTTP Basic authentication is disabled because the registration
	 * endpoints are intentionally public and are restricted by the authorization
	 * rules above.</p>
	 *
	 * @param http the {@link HttpSecurity} to configure
	 * @return a configured {@link SecurityFilterChain} bean
	 * @throws Exception if an error occurs while building the security configuration
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((authorizeHttpRequests) ->
						authorizeHttpRequests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll().
								requestMatchers(HttpMethod.POST, REGISTER_ENDPOINT, REGISTER_RECAPTCHA_ENDPOINT).permitAll().
//								requestMatchers("/actuator/**").permitAll().
								anyRequest().denyAll()
				);
		http.csrf((csrf) -> csrf.disable());
		http.httpBasic(httpSecurityHttpBasicConfigurer -> httpSecurityHttpBasicConfigurer.disable());

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
