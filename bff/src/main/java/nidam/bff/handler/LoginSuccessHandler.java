package nidam.bff.handler;

import nidam.bff.config.PostLoginUriConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.logging.Logger;

@Component
public class LoginSuccessHandler implements ServerAuthenticationSuccessHandler {
	private static final String DEFAULT_REDIRECT = "http://localhost:7080/react-ui/";

	private Logger log = Logger.getLogger(LoginSuccessHandler.class.getName());

	@Override
	public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange,
											  Authentication authentication) {
		ServerWebExchange exchange = webFilterExchange.getExchange();
		return exchange.getSession()
				.flatMap(session -> {
					// pull out what we saved earlier (or fall back)
					String redirect = session.getAttribute("POST_LOGIN_SUCCESS_URI");
					log.info("Redirect to: " + redirect);
					if (redirect == null || !redirect.startsWith("http")) {
						redirect = DEFAULT_REDIRECT;
					}

					ServerHttpResponse response = exchange.getResponse();
					response.setStatusCode(HttpStatus.FOUND);
					response.getHeaders().setLocation(URI.create(redirect));
					return response.setComplete();
				});
	}
}
