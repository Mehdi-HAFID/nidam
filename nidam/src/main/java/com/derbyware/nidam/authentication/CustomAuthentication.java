package com.derbyware.nidam.authentication;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.List;

public class CustomAuthentication extends JwtAuthenticationToken {
	private final List<String> authorities2;

	public CustomAuthentication(Jwt jwt, Collection<? extends GrantedAuthority> authorities, List<String> authorities2) {
		super(jwt, authorities);
		this.authorities2 = authorities2;
	}

	public List<String> getAuthorities2() {
		return authorities2;
	}

}
