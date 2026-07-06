package nidam.tokengenerator.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// TODO this is a work in progress, in the future the BFF should call /revoke
/**
 * OIDC logout response handler that removes the associated {@link OAuth2Authorization}
 * record from Redis before delegating to Spring Authorization Server's default logout behavior.
 *
 * <p>Active only when {@code nidam.session-mode=redis}. In Redis mode, authorization records
 * are explicitly persisted and must be explicitly removed on logout — Spring Authorization
 * Server does not call {@link OAuth2AuthorizationService#remove} during standard
 * OIDC RP-initiated logout, leaving stale records in Redis until their TTL expires.</p>
 *
 * <h3>Logout flow</h3>
 * <ol>
 *     <li>The BFF redirects the browser to {@code /auth/connect/logout} with an
 *     {@code id_token_hint} parameter.</li>
 *     <li>Spring Authorization Server's {@code OidcLogoutEndpointFilter} validates the
 *     request and produces an {@link OidcLogoutAuthenticationToken}.</li>
 *     <li>This handler is invoked, extracts the {@code id_token_hint}, looks up the
 *     corresponding {@link OAuth2Authorization} record, and removes it from Redis.</li>
 *     <li>Control is delegated to {@link OidcLogoutAuthenticationSuccessHandler} which
 *     invalidates the server-side session and redirects to the
 *     {@code post_logout_redirect_uri}.</li>
 * </ol>
 *
 * @see OidcLogoutAuthenticationSuccessHandler
 * @see OAuth2AuthorizationService
 */
@Component
@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "redis")
public class RedisOIDCLogoutResponseHandler implements AuthenticationSuccessHandler {

	private final OAuth2AuthorizationService authorizationService;
	private final OidcLogoutAuthenticationSuccessHandler defaultHandler;

	/**
	 * Constructs the handler with the Redis-backed {@link OAuth2AuthorizationService}.
	 * The {@link OidcLogoutAuthenticationSuccessHandler} is instantiated internally as
	 * it requires no external dependencies and represents fixed default behavior.
	 *
	 * @param authorizationService the service used to look up and remove the authorization
	 *                             record identified by the {@code id_token_hint}
	 */
	public RedisOIDCLogoutResponseHandler(OAuth2AuthorizationService authorizationService) {
		this.authorizationService = authorizationService;
		this.defaultHandler = new OidcLogoutAuthenticationSuccessHandler();
	}

	/**
	 * Removes the {@link OAuth2Authorization} record from Redis and delegates to the
	 * default logout handler.
	 * <p>
	 * If the {@code id_token_hint} in the {@link OidcLogoutAuthenticationToken} matches
	 * an existing authorization record, that record is removed before the default handler
	 * processes the post-logout redirect. If no matching record is found (e.g., already
	 * expired or removed), the default handler is still invoked normally.
	 *
	 * @param request        the HTTP request
	 * @param response       the HTTP response
	 * @param authentication the {@link OidcLogoutAuthenticationToken} produced by
	 *                       Spring Authorization Server after validating the logout request
	 * @throws IOException      if an I/O error occurs during redirect
	 * @throws ServletException if a servlet error occurs
	 */
	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {

		if (authentication instanceof OidcLogoutAuthenticationToken logoutToken) {
			String idTokenHint = logoutToken.getIdTokenHint();
			if (idTokenHint != null) {
				OAuth2Authorization authorization = authorizationService.findByToken(idTokenHint, new OAuth2TokenType(OidcParameterNames.ID_TOKEN));
				if (authorization != null) {
//						log.info("Removing authorization on logout for: " + authorization.getPrincipalName());
					authorizationService.remove(authorization);
				}
			}
		}
		defaultHandler.onAuthenticationSuccess(request, response, authentication);

	}
}
