package nidam.tokengenerator.redis.entity;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;

import nidam.tokengenerator.redis.service.RedisOAuth2AuthorizationService;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Redis entity representing a persisted OIDC authorization code grant.
 *
 * <p>Extends {@link OAuth2AuthorizationCodeGrantAuthorization} with an {@link IdToken}
 * field, making it the concrete entity class used in Nidam for all authorization
 * records. Since the registered client always requests the {@code openid} scope,
 * every authorization produces an ID token, and this subclass is always the one
 * persisted and retrieved from Redis.</p>
 *
 * <p>This is confirmed by the {@code _class} field stored in Redis:</p>
 * <pre>
 * _class = nidam.tokengenerator.redis.entity.redis.OidcAuthorizationCodeGrantAuthorization
 * </pre>
 *
 * @see OAuth2AuthorizationCodeGrantAuthorization
 * @see RedisOAuth2AuthorizationService
 */
public class OidcAuthorizationCodeGrantAuthorization extends OAuth2AuthorizationCodeGrantAuthorization {

	private final IdToken idToken;

	public OidcAuthorizationCodeGrantAuthorization(String id, String registeredClientId, String principalName,
	                                               Set<String> authorizedScopes, AccessToken accessToken, RefreshToken refreshToken, Principal principal,
	                                               OAuth2AuthorizationRequest authorizationRequest, AuthorizationCode authorizationCode, String state,
	                                               IdToken idToken) {
		super(id, registeredClientId, principalName, authorizedScopes, accessToken, refreshToken, principal, authorizationRequest,
				authorizationCode, state);
		this.idToken = idToken;
	}

	public IdToken getIdToken() {
		return this.idToken;
	}

	/**
	 * Represents the OIDC ID token issued alongside the access token.
	 * Extends {@link AbstractToken} and inherits the {@link org.springframework.data.redis.core.index.Indexed}
	 * {@code tokenValue} field, enabling lookup by ID token value — used during
	 * OIDC RP-initiated logout to locate and remove the authorization record
	 * via the {@code id_token_hint} parameter.
	 */
	public static class IdToken extends AbstractToken {

		private final ClaimsHolder claims;

		public IdToken(String tokenValue, Instant issuedAt, Instant expiresAt, boolean invalidated, ClaimsHolder claims) {
			super(tokenValue, issuedAt, expiresAt, invalidated);
			this.claims = claims;
		}

		public ClaimsHolder getClaims() {
			return this.claims;
		}

	}

}
