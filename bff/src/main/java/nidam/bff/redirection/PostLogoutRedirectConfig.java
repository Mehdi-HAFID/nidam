package nidam.bff.redirection;

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

/**
 * Configuration for handling post-logout redirects from the Authorization Server (OpenID Provider).
 *
 * <p>When a user logs out via RP-initiated logout, the OP (Authorization Server) will
 * redirect the browser back to the BFF’s {@code /post-logout} endpoint. This configuration
 * provides a {@link WebFilter} that processes that callback and issues a final redirect
 * to the SPA (React app).</p>
 *
 * <p>Core behavior:</p>
 * <ul>
 *   <li>Intercepts requests with a path starting with {@code /post-logout}.</li>
 *   <li>Extracts the {@code state} query parameter, which encodes the original
 *       post-logout redirect URI.</li>
 *   <li>Validates the decoded {@code state} URI against an allowlist from
 *       {@link LogoutProperties} to prevent open redirects.</li>
 *   <li>If valid, redirects the browser to the decoded URI. Otherwise, falls back to
 *       the configured default React URI ({@code react-uri}).</li>
 *   <li>Issues a {@code 302 Found} redirect to complete the logout flow.</li>
 * </ul>
 *
 * <p>Example flow:</p>
 * <ol>
 *   <li>User logs out from SPA → BFF → Authorization Server.</li>
 *   <li>Authorization Server redirects back to
 *       {@code http://localhost:7080/bff/post-logout?state=<encodedUri>}.</li>
 *   <li>The filter validates the {@code state} and redirects to
 *       {@code http://localhost:7080/react-ui} or another trusted URI.</li>
 * </ol>
 *
 * @see LogoutProperties for configuration of allowed redirect URI prefixes
 */
@Configuration
public class PostLogoutRedirectConfig {

	private static final Logger log = Logger.getLogger(PostLogoutRedirectConfig.class.getName());

	@Value("${react-uri}")
	private String defaultReactUri;

	private final LogoutProperties logoutProperties;

	private static final String POST_LOGOUT_ENDPOINT = "/post-logout";
	private static final String STATE_QUERY_PARAMETER = "state";

	public PostLogoutRedirectConfig(LogoutProperties logoutProperties) {
		this.logoutProperties = logoutProperties;
	}

	/**
	 * Defines a {@link WebFilter} that handles the post-logout callback from the OP.
	 *
	 * <p>Runs early in the chain ({@code @Order(-1)}) to catch {@code /post-logout}
	 * requests before other filters process them.</p>
	 *
	 * @return a filter that validates the {@code state} parameter and issues a redirect
	 *         to a trusted post-logout URI
	 */
	@Bean
	@Order(-1) // run early, before default filters
	public WebFilter onLogoutSuccessFilter() {
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
