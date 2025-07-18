package nidam.bff.config;

import nidam.bff.handler.CustomLogoutSuccessHandler;
import nidam.bff.handler.LoginSuccessHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.logging.Logger;
//import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

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
				)
				.oauth2Login(oauth2 -> oauth2
						.authenticationSuccessHandler(loginSuccessHandler))  // Enables login with the authorization server
				.oauth2Client(Customizer.withDefaults()) // Enables TokenRelay

				.logout(logout -> logout
						.logoutUrl("/bff/logout")  // or whatever logout endpoint you use
						.logoutSuccessHandler(new CustomLogoutSuccessHandler(clients))
				)

				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository())
				);

		return http.build();
	}

//	private ServerAuthenticationSuccessHandler postLoginSuccessHandler() {
//		final String DEFAULT_REDIRECT = "http://localhost:7080/react-ui";
//		return (webFilterExchange, authentication) -> {
//			String redirectUri = webFilterExchange.getExchange()
//					.getRequest()
//					.getQueryParams()
//					.getFirst("post_login_success_uri");
//			log.info("redirectUri: " + redirectUri);
//
//			if (redirectUri == null || redirectUri.isBlank()) {
//				redirectUri = DEFAULT_REDIRECT;
//			}
//
//			RedirectServerAuthenticationSuccessHandler delegate = new RedirectServerAuthenticationSuccessHandler(redirectUri);
//
//			return delegate.onAuthenticationSuccess(webFilterExchange, authentication);
//		};
//	}

//	private ServerAuthenticationSuccessHandler postLoginSuccessHandler() {
//		final String DEFAULT_REDIRECT = "http://localhost:7080/react-ui/";
//
//		return (webExchange, authentication) -> webExchange.getExchange().getSession().map(session -> {
//			String uri = (String) session.getAttributes().get("POST_LOGIN_SUCCESS_URI");
//			log.info("postLoginSuccessHandler().uri: " + uri);
//			if (uri == null || !uri.startsWith("http")) {
//				uri = DEFAULT_REDIRECT;
//			}
//			return uri;
//		}).flatMap(uri -> {
//			ServerHttpResponse response = webExchange.getExchange().getResponse();
//			response.setStatusCode(HttpStatus.FOUND);
//			response.getHeaders().setLocation(URI.create(uri));
//			return response.setComplete();
//		});
//	}

	private ServerCsrfTokenRepository csrfTokenRepository() {
		// This makes sure XSRF-TOKEN is stored in a cookie that's accessible to JavaScript (HttpOnly = false)
		CookieServerCsrfTokenRepository repo = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
		repo.setCookiePath("/"); // optional: make it accessible to the whole app
		return repo;
	}

//	@Bean
//	public WebFilter postLoginRedirectFilter() {
//		return (exchange, chain) -> {
//
//			log.info("exchange.getRequest().getURI().getPath(): " + exchange.getRequest().getURI().getPath());
//			if (exchange.getRequest().getURI().getPath().startsWith("/login/oauth2/code/")) {
//				// Check if the state param contains our redirect URI
//				String state = exchange.getRequest().getQueryParams().getFirst("state");
//				log.info("state: " + state);
//				if (state != null && state.startsWith("http")) {
//					ServerHttpResponse response = exchange.getResponse();
//					response.setStatusCode(HttpStatus.FOUND);
//					response.getHeaders().setLocation(URI.create(state));
//					return response.setComplete();
//				}
//			}
//
//			return chain.filter(exchange);
//		};
//	}

//	@Bean
//	public WebFilter postLoginSuccessUriFilter() {
//		return (exchange, chain) -> {
//			log.info("exchange.getRequest().getQueryParams().getFirst(\"post_login_success_uri\"): "
//					+ exchange.getRequest().getQueryParams().getFirst("post_login_success_uri"));
//			exchange.getRequest().getQueryParams().forEach((param, param2) -> log.info("PARAM: " + param + " -> " + param2));
//			String redirectUri = exchange.getRequest().getQueryParams().getFirst("post_login_success_uri");
//
//			if (redirectUri != null && redirectUri.startsWith("http")) {
//				return exchange.getSession()
//						.doOnNext(session -> session.getAttributes().put("POST_LOGIN_SUCCESS_URI", redirectUri))
//						.then(chain.filter(exchange));
//			}
//
//			return chain.filter(exchange);
//		};
//	}

	@Bean
	public WebFilter csrfTokenWebFilter() {
		return (exchange, chain) -> exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty())
				.cast(CsrfToken.class)
				.flatMap(token -> {

					// Only set the cookie if it's not already present or changed
					String currentCookie = exchange.getRequest().getCookies().getFirst("XSRF-TOKEN") != null
							? exchange.getRequest().getCookies().getFirst("XSRF-TOKEN").getValue()
							: null;
					log.info("currentCookie: " + currentCookie);

					if (!token.getToken().equals(currentCookie)) {
						ResponseCookie cookie = ResponseCookie.from("XSRF-TOKEN", token.getToken())
								.path("/") // match the path of your app
								.httpOnly(false) // must be accessible to JS
								.secure(false) // set to true if using HTTPS
								.build();
						log.info("Setting new XSRF-TOKEN cookie: {} " + cookie);
						exchange.getResponse().addCookie(cookie);
					}
					return chain.filter(exchange);
				})
				.switchIfEmpty(chain.filter(exchange));
	}
}
