package com.derbyware.nidam;

import com.derbyware.nidam.authentication.JwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

//@Configuration TODO this is removed because com.c4-soft.springaddons.spring-addons-starter-oidc is taking over the configuration
public class ProjectConfig {

	@Value("${keySetURI}")
	private String keySetUri;

//	private final JwtAuthenticationConverter converter;
//
//	public ProjectConfig(JwtAuthenticationConverter converter) {
//		this.converter = converter;
//	}

//	@Bean
//	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//		http.oauth2ResourceServer(
//				c -> c.jwt(
//						j -> j.jwkSetUri(keySetUri)
////								.jwtAuthenticationConverter(converter)
//				)
//		);
//		http.authorizeHttpRequests(
//				c -> c.anyRequest().authenticated()
//		);
//		return http.build();
//	}
}