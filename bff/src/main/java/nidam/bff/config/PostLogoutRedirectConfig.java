package nidam.bff.config;

import nidam.bff.config.properties.LogoutProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.WebFilter;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

@Configuration
public class PostLogoutRedirectConfig {

	private static final Logger log = Logger.getLogger(PostLogoutRedirectConfig.class.getName());

	@Value("${react-uri}")
	private String defaultReactUri;

	// This could be `LogoutProperties` if you prefer
	private final LogoutProperties logoutProperties;

	private static final String POST_LOGOUT_ENDPOINT = "/post-logout";
	private static final String STATE_QUERY_PARAMETER = "state";

	public PostLogoutRedirectConfig(LogoutProperties logoutProperties) {
		this.logoutProperties = logoutProperties;
	}

	@Bean
	@Order(-200) // run early, before default filters
	public WebFilter postLogoutRedirectFilter() {
		return (exchange, chain) -> {
			String path = exchange.getRequest().getURI().getPath();

			if (!path.startsWith(POST_LOGOUT_ENDPOINT)) {
				// Skip unless we’re on the OP → BFF post-logout callback
				return chain.filter(exchange);
			}

			// Grab `state` param from OP logout redirect
			String stateParam = exchange.getRequest().getQueryParams().getFirst(STATE_QUERY_PARAMETER);
			String resolvedUri = defaultReactUri; // fallback

			if (stateParam != null && !stateParam.isEmpty()) {
				String decoded = URLDecoder.decode(stateParam, StandardCharsets.UTF_8);
				log.info("post-logout state: " + decoded);

				boolean isAllowed = logoutProperties.getAllowedRedirectUriPrefixes() != null &&
						logoutProperties.getAllowedRedirectUriPrefixes().stream().anyMatch(decoded::startsWith);

				if (isAllowed) {
					resolvedUri = decoded;
				} else {
					log.warning("Rejected untrusted post-logout state: " + decoded +
							". Allowed prefixes: " + logoutProperties.getAllowedRedirectUriPrefixes());
				}
			}

			// Do a 302 redirect
			ServerHttpResponse response = exchange.getResponse();
			response.setStatusCode(HttpStatus.FOUND);
			response.getHeaders().setLocation(URI.create(resolvedUri));
			return response.setComplete();
		};
	}
}
