package nidam.nidam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
public class ProjectConfig {

    @Value("${issuer}")
    private String issuer;

    @Value("${audience:client}")
    private String expectedAudience;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProps securityProps) throws Exception {
        http
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers(securityProps.getPermitAll().toArray(new String[0])).permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2ResourceServer(
                        configurer -> configurer.jwt(
                                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Object rawAuthorities = jwt.getClaims().get("authorities");
            if (rawAuthorities instanceof Collection<?> list) {
                for (Object item : list) {
                    if (item instanceof String authority) {
                        authorities.add(new SimpleGrantedAuthority(authority));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> {
            List<String> audList = jwt.getAudience();
            if (audList == null || !audList.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience value", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    @Bean
    public JwtDecoder jwtDecoder(OAuth2TokenValidator<Jwt> audienceValidator) {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuer);
        jwtDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator
                ));
        return jwtDecoder;
    }

}