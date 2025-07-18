package nidam.bff.handler;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import reactor.core.publisher.Mono;

import java.net.URI;

public class CustomLogoutSuccessHandler extends RedirectServerLogoutSuccessHandler {
	private static final String HEADER_NAME = "X-POST-LOGOUT-SUCCESS-URI";
	private static final String DEFAULT_REDIRECT = "http://localhost:7080/react-ui";

	private final OidcClientInitiatedServerLogoutSuccessHandler delegate;

	public CustomLogoutSuccessHandler(ReactiveClientRegistrationRepository clientRegistrationRepository) {
		this.delegate = new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
		this.delegate.setPostLogoutRedirectUri(DEFAULT_REDIRECT);
	}

	@Override
	public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
		String redirectUri = exchange.getExchange()
				.getRequest()
				.getHeaders()
				.getFirst(HEADER_NAME);

		if (redirectUri != null) {
			delegate.setLogoutSuccessUrl(URI.create(redirectUri));
		} else {
			delegate.setLogoutSuccessUrl(URI.create(DEFAULT_REDIRECT));
		}

		return delegate.onLogoutSuccess(exchange, authentication);
	}
}
