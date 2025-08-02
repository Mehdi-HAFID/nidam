package nidam.nidam.config;

import nidam.nidam.config.validator.AudienceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * Security configuration class for the Resource Server.
 *
 * <p>This configuration sets up JWT-based OAuth2 resource server support using Spring Security.
 * It includes custom authority mapping, audience validation, and clock skew tolerance.</p>
 */
@Configuration
@EnableConfigurationProperties(SecurityProps.class)
public class SecurityConfig {

    /**
     * Claim name in the JWT token that contains granted authorities.
     */
    public static final String CLAIM_AUTHORITIES = "authorities";

    private static final Logger log = Logger.getLogger(SecurityConfig.class.getName());

    /**
     * Expected issuer URL from the Authorization Server.
     */
    @Value("${issuer}")
    private String issuer;

    /**
     * Expected audience value in the JWT token.
     * Defaults to "client" if not explicitly configured.
     */
    @Value("${audience:client}")
    private String expectedAudience;

    /**
     * Configures the main security filter chain for the application.
     *
     * <p>This method:
     * <ul>
     *   <li>Applies permit-all access to paths defined in {@link SecurityProps}.</li>
     *   <li>Requires authentication for all other paths.</li>
     *   <li>Sets up the application as an OAuth2 resource server using JWTs.</li>
     * </ul>
     * </p>
     *
     * @param http the {@link HttpSecurity} object provided by Spring Security
     * @param securityProps the custom configuration properties that define permit-all paths
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception in case of configuration errors
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProps securityProps) throws Exception {
        http
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(securityProps.getPermitAll().toArray(new String[0])).permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2ResourceServer(
                        configurer ->
                                configurer.jwt(jwt ->
                                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * Creates a {@link JwtAuthenticationConverter} that extracts authorities
     * from the custom "authorities" claim in the JWT token.
     *
     * @return a converter that maps string-based authorities to {@link SimpleGrantedAuthority}
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
//            log.info("JWT Claims: " + jwt.getClaims()); causes too many logs
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Object rawAuthorities = jwt.getClaims().get(CLAIM_AUTHORITIES);
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

    /**
     * Configures the {@link JwtDecoder} used to validate and decode JWT tokens.
     *
     * <p>This decoder:
     * <ul>
     *   <li>Leverages Spring Security’s `JwtDecoders.fromIssuerLocation(issuer)` to resolve the JWK Set URI
     *   (`/.well-known/openid-configuration` → `jwks_uri`) from the issuer and configure a `NimbusJwtDecoder`
     *   with the retrieved key set for JWT signature validation.</li>
     *   <li>Validates standard JWT claims including issuer, timestamps, and audience.</li>
     *   <li>Applies a 60-second clock skew for improved resilience to clock drift.</li>
     * </ul>
     * </p>
     *
     * @return the configured {@link JwtDecoder}
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        JwtTimestampValidator timestampValidatorWithSkew  = new JwtTimestampValidator(Duration.ofSeconds(60));

        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuer);
        DelegatingOAuth2TokenValidator<Jwt> jwtValidator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(expectedAudience),
                timestampValidatorWithSkew
        );
        jwtDecoder.setJwtValidator(jwtValidator);
        return jwtDecoder;
    }

}