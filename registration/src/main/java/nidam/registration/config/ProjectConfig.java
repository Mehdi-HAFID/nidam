package nidam.registration.config;

import jakarta.servlet.DispatcherType;
import nidam.registration.config.properties.PasswordProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Configuration
@EnableFeignClients(basePackages = "nidam.registration.proxy")
public class ProjectConfig {

	private final Logger log = Logger.getLogger(ProjectConfig.class.getName());

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((authorizeHttpRequests) ->
						authorizeHttpRequests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll().
								requestMatchers(HttpMethod.POST,"/register", "/registerCaptcha").permitAll().
								anyRequest().denyAll()
				);
		http.csrf((csrf) -> csrf.disable());
		http.httpBasic(httpSecurityHttpBasicConfigurer -> httpSecurityHttpBasicConfigurer.disable());
		http.cors(c -> {
			CorsConfigurationSource source = request -> {
				CorsConfiguration config = new CorsConfiguration();
				config.setAllowedOrigins(List.of(
//						"*",
						"http://localhost:4001", "http://127.0.0.1:4001",
						"http://localhost:7080", "http://127.0.0.1:7080"));
				config.setAllowedMethods(List.of("*"));
				config.setAllowedHeaders(List.of("*"));
				return config;
			};
			c.configurationSource(source);
		});
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder(PasswordProperties passwordProperties) {
		log.info("encoders: " + passwordProperties.getEncoders());

		Map<String, Supplier<PasswordEncoder>> encoderSuppliers = Map.of(
				"bcrypt", () -> new BCryptPasswordEncoder(),
				"argon2", () -> Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"pbkdf2", () -> Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
				"scrypt", () -> SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8()
		);

		Map<String, PasswordEncoder> encodersMapping = passwordProperties.getEncoders().stream()
				.filter(key1 -> encoderSuppliers.containsKey(key1))
				.collect(Collectors.toMap(Function.identity(), key -> encoderSuppliers.get(key).get()));


		// first in list used to encode
		DelegatingPasswordEncoder passwordEncoder = new DelegatingPasswordEncoder(passwordProperties.getEncoders().getFirst(), encodersMapping);
		// use this encoder if {id} does not exist
		passwordEncoder.setDefaultPasswordEncoderForMatches(
				encoderSuppliers.getOrDefault(passwordProperties.getIdless(), () -> SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8()).get()
		);
		return passwordEncoder;
	}

}
