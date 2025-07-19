package nidam.bff.config;

import nidam.bff.handler.LoginSuccessHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.logging.Logger;

@Configuration
public class SecurityConfig {

	private Logger log = Logger.getLogger(SecurityConfig.class.getName());

	@Autowired
	private LoginSuccessHandler loginSuccessHandler;

	@Bean
	public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http,
													  ReactiveClientRegistrationRepository clients) {
		http
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers("/api/**", "/login/**", "/oauth2/**", "/logout", "/error", "/actuator/health/**").permitAll()
						.pathMatchers(HttpMethod.POST, "/logout").permitAll()
				)
				.oauth2Login(oauth2 -> oauth2
						.authenticationSuccessHandler(loginSuccessHandler))  // Enables login with the authorization server
				.oauth2Client(Customizer.withDefaults()) // Enables TokenRelay

				.logout(logout -> logout
						.logoutUrl("/logout")  // or whatever logout endpoint you use
						.logoutSuccessHandler(customLogoutSuccessHandler())
				)

				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
						.csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
				);

		return http.build();
	}


	@Bean
	public WebFilter csrfTokenWebFilter() {
		return (exchange, chain) -> exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty())
				.cast(CsrfToken.class)
				.switchIfEmpty(
						exchange.getSession().then(Mono.defer(() ->
								exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty()).cast(CsrfToken.class)
						))
				)
				.flatMap(token -> {

					// Only set the cookie if it's not already present or changed
					String currentCookie = exchange.getRequest().getCookies().getFirst("XSRF-TOKEN") != null
							? exchange.getRequest().getCookies().getFirst("XSRF-TOKEN").getValue()
							: null;
					log.info("csrfTokenWebFilter() currentCookie: " + currentCookie);

					if (!token.getToken().equals(currentCookie)) {
						ResponseCookie cookie = ResponseCookie.from("XSRF-TOKEN", token.getToken())
								.path("/") // match the path of your app
								.sameSite("Lax")
								.httpOnly(false) // must be accessible to JS
								.secure(false) // set to true if using HTTPS
								.build();
						log.info("csrfTokenWebFilter()  Setting new XSRF-TOKEN cookie: {} " + cookie);
						exchange.getResponse().addCookie(cookie);
					}
					return chain.filter(exchange);
				})
				.switchIfEmpty(chain.filter(exchange));
	}

	@Bean
	public ServerLogoutSuccessHandler customLogoutSuccessHandler() {
		return (exchange, authentication) -> {
			ServerWebExchange webExchange = exchange.getExchange();
			ServerHttpResponse response = webExchange.getResponse();

			if (authentication instanceof OAuth2AuthenticationToken oauth2Auth) {
				OidcUser oidcUser = (OidcUser) oauth2Auth.getPrincipal();
				String idToken = oidcUser.getIdToken().getTokenValue();

				// TODO Next: extract the value from the SPA X-POST-LOGOUT-SUCCESS-URI and use it, also this
				//  http://localhost:7080/auth/connect/logout should extracted from application.yml
				String redirectUriRaw = webExchange.getRequest()
						.getQueryParams()
						.getFirst("X-POST-LOGOUT-SUCCESS-URI");

				String redirectUri = UriComponentsBuilder
						.fromUriString("http://localhost:7080/auth/connect/logout")
						.queryParam("id_token_hint", idToken)
						.queryParam("post_logout_redirect_uri", "http://localhost:7080/react-ui")
						.queryParam("client_id", "client")
						.build()
						.toUriString();


				response.setStatusCode(HttpStatus.ACCEPTED);
				response.getHeaders().setLocation(URI.create(redirectUri));

				// Clear XSRF-TOKEN manually (Spring Security doesn't delete it)
				response.addCookie(ResponseCookie.from("XSRF-TOKEN", "")
						.path("/")
						.sameSite("Lax")
						.maxAge(Duration.ZERO)
						.httpOnly(false)
						.build());

				// Optionally clear SESSION cookie (for consistency)
				response.addCookie(ResponseCookie.from("SESSION", "")
						.path("/")
						.sameSite("Lax")
						.maxAge(Duration.ZERO)
						.httpOnly(true)
						.build());

				return response.setComplete();
			}

			// fallback if not authenticated
			response.setStatusCode(HttpStatus.NO_CONTENT);
			return response.setComplete();
		};
	}

}
