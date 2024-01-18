package com.derbyware.registration.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Configuration
@EnableFeignClients(basePackages = "com.derbyware.registration.proxy")
public class ProjectConfig {

	private Logger log = Logger.getLogger(ProjectConfig.class.getName());

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
						"http://localhost:4001", "http://127.0.0.1:4001"));
				config.setAllowedMethods(List.of("*"));
				config.setAllowedHeaders(List.of("*"));
				return config;
			};
			c.configurationSource(source);
		});
		return http.build();
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
//			case "scrypt":
//				return SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8();
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
}
