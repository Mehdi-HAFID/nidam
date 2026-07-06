package nidam.tokengenerator.redis.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import nidam.tokengenerator.redis.convert.BytesToClaimsHolderConverter;
import nidam.tokengenerator.redis.convert.ClaimsHolderToBytesConverter;
import nidam.tokengenerator.redis.service.RedisOAuth2AuthorizationService;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Abstract base entity representing a persisted OAuth2 authorization grant,
 * stored in Redis via Spring Data Redis {@link RedisHash} repositories.
 *
 * <p>This class is the root of the authorization grant entity hierarchy used by
 * {@link RedisOAuth2AuthorizationService}
 * as the Redis-backed storage model for
 * {@link org.springframework.security.oauth2.server.authorization.OAuth2Authorization}.
 * Each concrete subclass corresponds to a specific grant type:</p>
 * <ul>
 *     <li>{@link OAuth2AuthorizationCodeGrantAuthorization} — authorization code flow</li>
 *     <li>{@link OidcAuthorizationCodeGrantAuthorization} — OIDC authorization code flow
 *     (adds an ID token)</li>
 * </ul>
 *
 * <p>Spring Data Redis flattens the fields of this entity and its subclasses into
 * dot-notation hash entries (e.g., {@code accessToken.tokenValue},
 * {@code accessToken.expiresAt}). Fields annotated with
 * {@link org.springframework.data.redis.core.index.Indexed} on nested
 * {@link AbstractToken} subclasses create secondary index {@code SET} keys in Redis,
 * enabling lookup by token value via repository finder methods.</p>
 *
 * <p>Complex types that cannot be flattened automatically —
 * such as the {@code principal} ({@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken})
 * and {@code authorizationRequest} ({@link org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest})
 * — are serialized to {@code byte[]} by custom converters registered via
 * {@link org.springframework.data.redis.core.convert.RedisCustomConversions}.</p>
 *
 * <p>All keys are stored under the namespace
 * {@code nidam:token-generator:oauth2:authorization}. TTL is set explicitly
 * after each save via {@link org.springframework.data.redis.core.StringRedisTemplate}
 * rather than {@link org.springframework.data.redis.core.TimeToLive}, since the
 * latter is not reliably applied by Spring Data Redis on abstract classes.</p>
 */
@RedisHash("${spring.session.redis.namespace:nidam:token-generator}:oauth2:authorization")
public abstract class OAuth2AuthorizationGrantAuthorization {
	private static final Logger log = Logger.getLogger(OAuth2AuthorizationGrantAuthorization.class.getName());

	@Id
	private final String id;

	private final String registeredClientId;

	private final String principalName;

	private final Set<String> authorizedScopes;

	private final AccessToken accessToken;

	private final RefreshToken refreshToken;

	// @fold:on
	protected OAuth2AuthorizationGrantAuthorization(String id, String registeredClientId, String principalName,
	                                                Set<String> authorizedScopes, AccessToken accessToken, RefreshToken refreshToken) {
		this.id = id;
		this.registeredClientId = registeredClientId;
		this.principalName = principalName;
		this.authorizedScopes = authorizedScopes;
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
	}

	public String getId() {
		return this.id;
	}

	public String getRegisteredClientId() {
		return this.registeredClientId;
	}

	public String getPrincipalName() {
		return this.principalName;
	}

	public Set<String> getAuthorizedScopes() {
		return this.authorizedScopes;
	}

	public AccessToken getAccessToken() {
		return this.accessToken;
	}

	public RefreshToken getRefreshToken() {
		return this.refreshToken;
	}

	/**
	 * Base class for all token types stored within an authorization grant entity.
	 * <p>
	 * The {@code tokenValue} field is annotated with {@link Indexed} so that Spring
	 * Data Redis creates a secondary index {@code SET} key for each token, enabling
	 * the repository to look up an authorization by any of its token values.
	 */
	protected abstract static class AbstractToken {

		@Indexed
		private final String tokenValue;

		private final Instant issuedAt;

		private final Instant expiresAt;

		private final boolean invalidated;

		protected AbstractToken(String tokenValue, Instant issuedAt, Instant expiresAt, boolean invalidated) {
			this.tokenValue = tokenValue;
			this.issuedAt = issuedAt;
			this.expiresAt = expiresAt;
			this.invalidated = invalidated;
		}

		public String getTokenValue() {
			return this.tokenValue;
		}

		public Instant getIssuedAt() {
			return this.issuedAt;
		}

		public Instant getExpiresAt() {
			return this.expiresAt;
		}

		public boolean isInvalidated() {
			return this.invalidated;
		}

	}

	/**
	 * Holds a map of JWT claims associated with a token (access token or ID token).
	 * <p>
	 * Stored as a serialized JSON blob in the Redis hash rather than as flattened
	 * fields, because the claim values are typed as {@code Object} and require
	 * the {@link ClaimsHolderToBytesConverter}
	 * and {@link BytesToClaimsHolderConverter}
	 * for bidirectional serialization.
	 */
	public static class ClaimsHolder {

		private final Map<String, Object> claims;

		@JsonCreator
		public ClaimsHolder(@JsonProperty("claims") Map<String, Object> claims) {
			this.claims = claims;
		}

		public Map<String, Object> getClaims() {
			return this.claims;
		}

	}

	/**
	 * Represents the OAuth2 access token within an authorization grant entity.
	 * Extends {@link AbstractToken} with bearer token metadata including token type,
	 * authorized scopes, token format, and JWT claims.
	 */
	public static class AccessToken extends AbstractToken {

		private final OAuth2AccessToken.TokenType tokenType;

		private final Set<String> scopes;

		private final OAuth2TokenFormat tokenFormat;

		private final ClaimsHolder claims;

		public AccessToken(String tokenValue, Instant issuedAt, Instant expiresAt, boolean invalidated,
		                   OAuth2AccessToken.TokenType tokenType, Set<String> scopes, OAuth2TokenFormat tokenFormat,
		                   ClaimsHolder claims) {
			super(tokenValue, issuedAt, expiresAt, invalidated);
			this.tokenType = tokenType;
			this.scopes = scopes;
			this.tokenFormat = tokenFormat;
			this.claims = claims;
		}

		public OAuth2AccessToken.TokenType getTokenType() {
			return this.tokenType;
		}

		public Set<String> getScopes() {
			return this.scopes;
		}

		public OAuth2TokenFormat getTokenFormat() {
			return this.tokenFormat;
		}

		public ClaimsHolder getClaims() {
			return this.claims;
		}

	}

	/**
	 * Represents the OAuth2 refresh token within an authorization grant entity.
	 * Currently unused in Nidam as refresh tokens are disabled pending resolution
	 * of a Spring Authorization Server logout issue.
	 */
	public static class RefreshToken extends AbstractToken {

		public RefreshToken(String tokenValue, Instant issuedAt, Instant expiresAt, boolean invalidated) {
			super(tokenValue, issuedAt, expiresAt, invalidated);
		}

	}

}
