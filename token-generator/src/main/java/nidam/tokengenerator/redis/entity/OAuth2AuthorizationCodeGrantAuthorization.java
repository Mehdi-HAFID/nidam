package nidam.tokengenerator.redis.entity;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;

import nidam.tokengenerator.redis.repository.OAuth2AuthorizationGrantAuthorizationRepository;
import org.springframework.data.redis.core.index.Indexed;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Redis entity representing a persisted OAuth2 authorization code grant.
 *
 * <p>Extends {@link OAuth2AuthorizationGrantAuthorization} with the additional fields
 * specific to the authorization code grant type: the authenticated principal, the
 * original authorization request, the authorization code, and the OAuth2 state
 * parameter.</p>
 *
 * <p>The {@code state} field is annotated with
 * {@link org.springframework.data.redis.core.index.Indexed} so that Spring Data Redis
 * creates a secondary index {@code SET} key, enabling lookup by state value via
 * {@link OAuth2AuthorizationGrantAuthorizationRepository#findByState}.
 * This is used during the authorization consent flow to correlate the callback
 * with the original request.</p>
 *
 * <p>In Nidam, this class is never instantiated directly — it is always the OIDC
 * subclass {@link OidcAuthorizationCodeGrantAuthorization} that is persisted,
 * since the client always requests the {@code openid} scope.</p>
 *
 * @see OidcAuthorizationCodeGrantAuthorization
 * @see OAuth2AuthorizationGrantAuthorization
 */
public class OAuth2AuthorizationCodeGrantAuthorization extends OAuth2AuthorizationGrantAuthorization {

	private final Principal principal;

	private final OAuth2AuthorizationRequest authorizationRequest;

	private final AuthorizationCode authorizationCode;

	@Indexed
	private final String state; // Used to correlate the request during the authorization consent flow

	public OAuth2AuthorizationCodeGrantAuthorization(String id, String registeredClientId, String principalName,
	                                                 Set<String> authorizedScopes, AccessToken accessToken, RefreshToken refreshToken, Principal principal,
	                                                 OAuth2AuthorizationRequest authorizationRequest, AuthorizationCode authorizationCode, String state) {
		super(id, registeredClientId, principalName, authorizedScopes, accessToken, refreshToken);
		this.principal = principal;
		this.authorizationRequest = authorizationRequest;
		this.authorizationCode = authorizationCode;
		this.state = state;
	}

	public Principal getPrincipal() {
		return this.principal;
	}

	public OAuth2AuthorizationRequest getAuthorizationRequest() {
		return this.authorizationRequest;
	}

	public AuthorizationCode getAuthorizationCode() {
		return this.authorizationCode;
	}

	public String getState() {
		return this.state;
	}

	/**
	 * Represents the authorization code issued during the authorization code grant flow.
	 * Extends {@link AbstractToken} and inherits the {@link org.springframework.data.redis.core.index.Indexed}
	 * {@code tokenValue} field, enabling lookup by authorization code value during
	 * the token exchange step.
	 */
	public static class AuthorizationCode extends AbstractToken {

		public AuthorizationCode(String tokenValue, Instant issuedAt, Instant expiresAt, boolean invalidated) {
			super(tokenValue, issuedAt, expiresAt, invalidated);
		}

	}

}
