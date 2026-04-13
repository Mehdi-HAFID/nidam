package nidam.bff.redirection;

import nidam.bff.config.properties.LogoutProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Configures RP-initiated logout handling for a reactive Spring WebFlux BFF.
 *
 * <p>This component constructs the logout redirect URI to the Authorization Server's
 * (OpenID Provider) end-session endpoint and clears client-side authentication
 * cookies. It ensures the user is properly logged out from both the BFF and the
 * Authorization Server.</p>
 *
 * <p>Typical flow:</p>
 * <ol>
 *     <li>The SPA sends a logout request to the BFF.</li>
 *     <li>This handler builds a redirect URI containing the {@code id_token_hint}, {@code post_logout_redirect_uri},
 *     and {@code client_id} query parameters.</li>
 *     <li>The response sets HTTP status {@link org.springframework.http.HttpStatus#ACCEPTED} and redirects
 *     the browser to the OP logout endpoint.</li>
 *     <li>Cookies such as {@code XSRF-TOKEN} and {@code SESSION} are cleared, and the
 *     {@link org.springframework.web.server.WebSession} is invalidated.</li>
 * </ol>
 *
 * <p>Security: The post-logout redirect URI provided by the SPA is validated
 * against allowed prefixes defined in {@link LogoutProperties} to prevent open redirects.</p>
 */

@Configuration
public class LogoutRedirectConfig implements ServerLogoutSuccessHandler {

	private static final Logger log = Logger.getLogger(LogoutRedirectConfig.class.getName());

	private static final String STATE_QUERY_PARAMETER = "state";
	public static final String COOKIE_XSRF_TOKEN = "XSRF-TOKEN";
	public static final String COOKIE_SESSION = "SESSION";

	private final LogoutProperties logoutProperties;

	@Value("${client.id}")
	private String clientId;

	public LogoutRedirectConfig(LogoutProperties logoutProperties) {
		this.logoutProperties = logoutProperties;
	}

	/**
	 * Returns a {@link org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler}
	 * that handles RP-initiated logout for OIDC users.
	 *
	 * <p>If the user is authenticated via {@link org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken},
	 * the handler constructs a redirect URI including
	 * <ul>
	 *      <li>{@code id_token_hint} - the ID token to identify the session</li>
	 *      <li>{@code post_logout_redirect_uri} - where the OP should redirect back</li>
	 *      <li>{@code client_id} - to identify the Relying Party</li>
	 * </ul>
	 * sets HTTP 202 Accepted, clears cookies, and invalidates the {@link org.springframework.web.server.WebSession}.</p>
	 *
	 * <p>If the user is not authenticated, the response simply returns HTTP 204 No Content.</p>
	 *
	 * @return a reactive {@link org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler} for logout
	 */
	@Override
	public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {

		ServerWebExchange webExchange = exchange.getExchange();
		ServerHttpResponse response = webExchange.getResponse();

		if (authentication instanceof OAuth2AuthenticationToken oauth2Auth) {
			OidcUser oidcUser = (OidcUser) oauth2Auth.getPrincipal();
			String idToken = oidcUser.getIdToken().getTokenValue();

			String redirectUri = buildLogoutRedirectUri(webExchange, idToken);
			log.info("logout uri: " + redirectUri);

			response.setStatusCode(HttpStatus.ACCEPTED);
			response.getHeaders().setLocation(URI.create(redirectUri));

			clearCookies(response);

			// ✅ Invalidate the session server-side, aka remove SESSION cookie
			return webExchange.getSession()
					.flatMap(WebSession::invalidate)
					.then(response.setComplete());
		}

		// fallback if not authenticated
		response.setStatusCode(HttpStatus.NO_CONTENT);
		return response.setComplete();
	}

	/**
	 * Builds the full logout redirect URI to the Authorization Server's end-session endpoint.
	 *
	 * The URI is sent to the user's browser to redirect to the OP logout endpoint.
	 * <p>The URI includes the {@code id_token_hint}, the default or SPA-provided
	 * post-logout redirect URI, the client ID, and an encoded {@code state} parameter.</p>
	 *
	 * @param webExchange the current {@link org.springframework.web.server.ServerWebExchange}
	 * @param idToken     the OIDC ID token of the authenticated user
	 * @return a fully constructed URI string for RP-initiated logout
	 */
	private String buildLogoutRedirectUri(ServerWebExchange webExchange, String idToken) {
		String redirectUriRaw = webExchange.getRequest().getHeaders().getFirst(logoutProperties.getSuccessRedirectHeader());
//		log.info("buildLogoutRedirectUri redirectUriRaw: " + redirectUriRaw);

		String logoutRedirectUri = logoutProperties.getSuccessRedirectDefaultUri(); // use default in case spa did not provide one

		if (redirectUriRaw != null && redirectUriRaw.startsWith("http")) {
			boolean isAllowed = logoutProperties.getAllowedRedirectUriPrefixes() != null
					&& logoutProperties.getAllowedRedirectUriPrefixes().stream().anyMatch(redirectUriRaw::startsWith);
			log.info("logout uri isAllowed: " + isAllowed);

			if (isAllowed) {
				logoutRedirectUri = redirectUriRaw;
			} else {
				log.warning("Rejected untrusted post_logout_success_uri: " + redirectUriRaw +
						". Allowed prefixes: " + logoutProperties.getAllowedRedirectUriPrefixes());
			}
		}
		log.info("logoutRedirectUri: " + logoutRedirectUri);

		String encodedState = URLEncoder.encode(logoutRedirectUri, StandardCharsets.UTF_8);

		String redirectUri = UriComponentsBuilder
				.fromUriString(logoutProperties.getAuthServerUri())
				.queryParam(logoutProperties.getTokenHintParamName(), idToken)
				.queryParam(logoutProperties.getPostRedirectParamName(), logoutProperties.getBffPostLogoutUri())
				.queryParam(logoutProperties.getClientIdParamName(), clientId)
				.queryParam(STATE_QUERY_PARAMETER, encodedState)
				.build()
				.toUriString();
		return redirectUri;
	}
	/**
	 * Clears authentication-related cookies from the response to remove client-side traces.
	 *
	 * <p>Specifically, clears:</p>
	 * <ul>
	 *     <li>{@code XSRF-TOKEN}</li>
	 *     <li>{@code SESSION}</li>
	 * </ul>
	 *
	 * @param response the {@link org.springframework.http.server.reactive.ServerHttpResponse} to clear cookies from
	 */
	private void clearCookies(ServerHttpResponse response) {
		// Clear XSRF-TOKEN manually (Spring Security doesn't delete it)
//		log.info("Clearing Cookies");
		response.addCookie(ResponseCookie.from(COOKIE_XSRF_TOKEN, "")
				.path("/")
				.sameSite("Lax")
				.maxAge(Duration.ZERO)
				.httpOnly(false)
				.build());

		// Optionally clear SESSION cookie (for consistency)
		response.addCookie(ResponseCookie.from(COOKIE_SESSION, "")
				.path("/")
				.sameSite("Lax")
				.maxAge(Duration.ZERO)
				.httpOnly(true)
				.build());
	}
}
