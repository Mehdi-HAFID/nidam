package nidam.bff.handler;

import nidam.bff.config.properties.LoginProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.logging.Logger;

@Component
public class LoginSuccessHandler implements ServerAuthenticationSuccessHandler {

	@Value("${react-uri}")
	private String defaultReactUri;

	private final Logger log = Logger.getLogger(LoginSuccessHandler.class.getName());

	private final LoginProperties loginProperties;
//	private final ReactiveOAuth2AuthorizedClientService authorizedClientService;

	public LoginSuccessHandler(LoginProperties loginProperties
//			, ReactiveOAuth2AuthorizedClientService authorizedClientService
	) {
		this.loginProperties = loginProperties;
//		this.authorizedClientService = authorizedClientService;
	}

	@Override
	public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
		ServerWebExchange exchange = webFilterExchange.getExchange();
		return exchange.getSession()
				.flatMap(session -> {
					// pull out what we saved earlier (or fall back)

					String redirect = session.getAttribute(loginProperties.getSessionRedirectUriAttribute());
					log.info("onAuthenticationSuccess Redirect to: " + redirect);

					boolean isAllowed = redirect != null && loginProperties.getAllowedRedirectUriPrefixes().stream().anyMatch(redirect::startsWith);

					String finalRedirect = isAllowed ? redirect : defaultReactUri;

					if (!isAllowed) {
						log.warning("Untrusted or missing redirect URI in session. Falling back to default: " + finalRedirect);
					}

					ServerHttpResponse response = exchange.getResponse();
					response.setStatusCode(HttpStatus.FOUND);
					response.getHeaders().setLocation(URI.create(finalRedirect));
					return response.setComplete();
				});
	}

	// TODO IMPORTANT do not remove this is the version that support automatic refresh token, just waiting for the spring auth server bug to be fixed
//	@Override
//	public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
//		ServerWebExchange exchange = webFilterExchange.getExchange();
//
//		// Fallback for non-OAuth2 login
//		return exchange.getSession()
//				.flatMap(session -> authorizedClientService
//						.loadAuthorizedClient(
//								((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId(), authentication.getName())
//						.doOnNext(client -> {
//							String accessToken = client.getAccessToken().getTokenValue();
//							log.info("onAuthenticationSuccess accessToken: " + accessToken);
//							session.getAttributes().put("lastAccessToken", accessToken);
//						})
//						.then(Mono.defer(() -> {
//							String redirect = session.getAttribute(loginProperties.getSessionRedirectUriAttribute());
//							log.info("onAuthenticationSuccess Redirect to: " + redirect);
//
//							boolean isAllowed = redirect != null &&
//									loginProperties.getAllowedRedirectUriPrefixes().stream().anyMatch(redirect::startsWith);
//							String finalRedirect = isAllowed ? redirect : defaultReactUri;
//
//							if (!isAllowed) {
//								log.warning("Untrusted or missing redirect URI in session. Falling back to default: " + finalRedirect);
//							}
//
//							ServerHttpResponse response = exchange.getResponse();
//							response.setStatusCode(HttpStatus.FOUND);
//							response.getHeaders().setLocation(URI.create(finalRedirect));
//							return response.setComplete();
//						}))
//				);
//	}
}
