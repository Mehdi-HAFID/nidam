//package nidam.bff.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
//import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
//import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
//
//@Configuration
//public class OAuth2LoginConfig {
//
//	@Bean
//	public ServerOAuth2AuthorizationRequestResolver customAuthorizationRequestResolver(
//			ReactiveClientRegistrationRepository repo
//	) {
//		return (exchange, clientRegistrationId) ->
//				repo.findByRegistrationId(clientRegistrationId)
//						.map(clientRegistration -> {
//							String redirectUri = exchange.getRequest()
//									.getQueryParams()
//									.getFirst("post_login_success_uri");
//
//							OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest
//									.authorizationCode()
//									.authorizationUri(clientRegistration.getProviderDetails().getAuthorizationUri())
//									.clientId(clientRegistration.getClientId())
//									.redirectUri(clientRegistration.getRedirectUri())
//									.scopes(clientRegistration.getScopes())
//									.state(redirectUri) // put post-login URI in state
//									.additionalParameters(params -> params.put("nonce", "abc123")) // optional
//									.build()
//									.toBuilder();
//
//							return builder.build();
//						});
//	}
//
//	@Bean
//	public WebSessionServerOAuth2AuthorizationRequestRepository authorizationRequestRepository() {
//		return new WebSessionServerOAuth2AuthorizationRequestRepository();
//	}
//
//}



// removed from config logout, not working code

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

//import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
//import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
//
//private ServerCsrfTokenRepository csrfTokenRepository() {
//	// This makes sure XSRF-TOKEN is stored in a cookie that's accessible to JavaScript (HttpOnly = false)
//	CookieServerCsrfTokenRepository repo = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
//	repo.setCookiePath("/"); // optional: make it accessible to the whole app
//	return repo;
//}

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