package nidam.tokengenerator.redis.service;

import nidam.tokengenerator.redis.entity.OAuth2AuthorizationGrantAuthorization;
import nidam.tokengenerator.redis.config.RedisOAuth2Config;
import nidam.tokengenerator.redis.repository.OAuth2AuthorizationGrantAuthorizationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.convert.MappingRedisConverter;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

/**
 * Redis-backed implementation of {@link OAuth2AuthorizationService} for high availability
 * deployments of the token generator.
 *
 * <p>Replaces the default
 * {@link org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService}
 * so that authorization records — including authorization codes, access tokens, and ID tokens —
 * are shared across all token generator instances via Redis. Without this, an authorization
 * code issued by one instance would not be found by another during the token exchange,
 * producing an {@code invalid_grant} error.</p>
 *
 * <h3>Storage model</h3>
 * <p>Authorization records are persisted as
 * {@link OAuth2AuthorizationGrantAuthorization}
 * entities via a Spring Data Redis {@link org.springframework.data.redis.core.RedisHash}
 * repository. Spring Data Redis flattens the entity fields into dot-notation hash entries
 * and creates secondary index {@code SET} keys for {@code @Indexed} token value fields,
 * enabling the {@code findByToken} lookups required by Spring Authorization Server.</p>
 *
 * <h3>TTL management</h3>
 * <p>{@link org.springframework.data.redis.core.TimeToLive} on an abstract entity class
 * is not reliably applied by Spring Data Redis. Instead, TTL is set explicitly via
 * {@link StringRedisTemplate#expire} after each {@link #save} call. The TTL is calculated
 * as the access token's remaining lifetime plus a one-minute grace period, falling back to
 * 24 hours if no access token expiry is available.</p>
 *
 * <h3>Logout cleanup</h3>
 * <p>{@link #remove} is not called by Spring Authorization Server during standard
 * OIDC RP-initiated logout. Explicit removal on logout is handled by
 * {@link nidam.tokengenerator.config.RedisOIDCLogoutResponseHandler}, which calls
 * {@link #remove} before delegating to the default logout response handler.</p>
 *
 * @see RedisOAuth2Config
 * @see nidam.tokengenerator.config.RedisOIDCLogoutResponseHandler
 * @see OAuth2AuthorizationGrantAuthorization
 */
public class RedisOAuth2AuthorizationService implements OAuth2AuthorizationService {
	private final Logger log = Logger.getLogger(RedisOAuth2AuthorizationService.class.getName());

	private final RegisteredClientRepository registeredClientRepository;

	private final OAuth2AuthorizationGrantAuthorizationRepository authorizationGrantAuthorizationRepository;

	private final StringRedisTemplate stringRedisTemplate;

	@Value("${spring.session.redis.namespace:nidam:token-generator}:oauth2:authorization")
	private String OAuth2RedisHash;

	public RedisOAuth2AuthorizationService(RegisteredClientRepository registeredClientRepository,
										   OAuth2AuthorizationGrantAuthorizationRepository authorizationGrantAuthorizationRepository,
			                               StringRedisTemplate stringRedisTemplate) {
		Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
		Assert.notNull(authorizationGrantAuthorizationRepository, "authorizationGrantAuthorizationRepository cannot be null");
		this.registeredClientRepository = registeredClientRepository;
		this.authorizationGrantAuthorizationRepository = authorizationGrantAuthorizationRepository;
		this.stringRedisTemplate = stringRedisTemplate;
	}

	/**
	 * Persists the given {@link OAuth2Authorization} to Redis and sets explicit TTLs
	 * on the main HASH key and all secondary index {@code SET} keys.
	 * <p>
	 * Called by Spring Authorization Server twice during the authorization code flow:
	 * <ol>
	 *     <li>By {@code OAuth2AuthorizationCodeRequestAuthenticationProvider} when the
	 *     authorization request is processed — stores a pending authorization containing
	 *     only the authorization code. At this point no tokens are present, so the
	 *     fallback TTL of 1 minute applies, which is sufficient for the few milliseconds
	 *     until the BFF exchanges the code at {@code /oauth2/token}.</li>
	 *     <li>By {@code OAuth2AuthorizationCodeAuthenticationProvider} after the token
	 *     exchange — updates the record with the issued access token and ID token.
	 *     TTL is recalculated from the token expiry times.</li>
	 * </ol>
	 * <p>
	 * TTL is set explicitly via {@link org.springframework.data.redis.core.StringRedisTemplate#expire}
	 * rather than {@link org.springframework.data.redis.core.TimeToLive} because that
	 * annotation is not reliably applied by Spring Data Redis on abstract entity classes.
	 *
	 * @param authorization the authorization record to persist
	 */
	@Override
	public void save(OAuth2Authorization authorization) {
		Assert.notNull(authorization, "authorization cannot be null");
		log.info("Saving authorization with id: " + authorization.getId());
		OAuth2AuthorizationGrantAuthorization oAuth2AuthorizationGrantAuthorization = ModelMapper.convertOAuth2AuthorizationGrantAuthorization(authorization);
		authorizationGrantAuthorizationRepository.save(oAuth2AuthorizationGrantAuthorization);

		long ttlSeconds = determineTimeToLive(authorization);

		String prefix = OAuth2RedisHash + ":";
		Duration ttl = Duration.ofSeconds(ttlSeconds);
		stringRedisTemplate.expire(prefix + authorization.getId(), ttl);

		// Secondary index SET keys
		expireSETKeys(authorization, prefix, ttl);
	}

	/**
	 * Sets the TTL on all secondary index {@code SET} keys associated with the given
	 * authorization. Spring Data Redis creates one {@code SET} key per
	 * {@link org.springframework.data.redis.core.index.Indexed} field on the entity,
	 * enabling token-value-based lookups via the repository finder methods. These keys
	 * are separate from the main HASH key and must have their TTL set independently.
	 * <p>
	 * Covers: access token, authorization code, and OIDC ID token index keys.
	 * State index keys are not covered here since the {@code state} attribute is
	 * consumed and cleared before the second save, making it absent from the
	 * authorization object at TTL-setting time.
	 *
	 * @param authorization the authorization whose token index keys should be expired
	 * @param prefix        the Redis key prefix ({@code OAuth2RedisHash + ":"})
	 * @param ttl           the TTL duration to apply to each index key
	 */
	private void expireSETKeys(OAuth2Authorization authorization, String prefix, Duration ttl) {
		OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
		if (accessToken != null) {
			stringRedisTemplate.expire(prefix + "accessToken.tokenValue:" + accessToken.getToken().getTokenValue(), ttl);
		}

		OAuth2Authorization.Token<OAuth2AuthorizationCode> authCode = authorization.getToken(OAuth2AuthorizationCode.class);
		if (authCode != null) {
			stringRedisTemplate.expire(prefix + "authorizationCode.tokenValue:" + authCode.getToken().getTokenValue(), ttl);
		}

		OAuth2Authorization.Token<OidcIdToken> idToken = authorization.getToken(OidcIdToken.class);
		if (idToken != null) {
			stringRedisTemplate.expire(prefix + "idToken.tokenValue:" + idToken.getToken().getTokenValue(), ttl);
		}
	}

	/**
	 * Determines the TTL in seconds for an authorization record based on the latest
	 * expiry time across all present tokens, plus a one-minute grace period.
	 * <p>
	 * The grace period ensures the record remains available for any in-flight requests
	 * (such as token introspection or logout) that arrive close to the expiry boundary.
	 * <p>
	 * Token precedence by TTL length (longest wins via {@link Math#max}):
	 * <ol>
	 *     <li>Access token — typically 12 hours in Nidam</li>
	 *     <li>Refresh token — disabled in Nidam, included for completeness</li>
	 *     <li>OIDC ID token — same expiry as access token in Nidam</li>
	 * </ol>
	 * <p>
	 * If none of the above tokens are present (i.e. the first save during the
	 * authorization request phase, before the token exchange), the fallback TTL of
	 * 1 minute is returned. This is intentionally short since the authorization code
	 * itself expires in 5 minutes by default, and the record is superseded by the
	 * second save immediately after the token exchange.
	 *
	 * @param authorization the authorization whose token expiry times are examined
	 * @return the TTL in seconds to apply to the Redis keys
	 */
	private long determineTimeToLive(OAuth2Authorization authorization) {
		long ttlSeconds = 60; // fallback 1 minute
		Instant now = Instant.now();
		long secondsUntilExpiry = 0;
		if (authorization.getAccessToken() != null && authorization.getAccessToken().getToken().getExpiresAt() != null) {
			secondsUntilExpiry = ChronoUnit.SECONDS.between(now, authorization.getAccessToken().getToken().getExpiresAt());
//			log.info("Access token expires in " + secondsUntilExpiry + " seconds");
			ttlSeconds = Math.max(ttlSeconds, secondsUntilExpiry + 60);
		}
		if (authorization.getRefreshToken() != null) {
			secondsUntilExpiry = ChronoUnit.SECONDS.between(now, authorization.getRefreshToken().getToken().getExpiresAt());
//			log.info("Refresh token expires in " + secondsUntilExpiry + " seconds");
			ttlSeconds = Math.max(ttlSeconds, secondsUntilExpiry + 60);
		}

		if (authorization.getToken(OidcIdToken.class) != null) {
			secondsUntilExpiry = ChronoUnit.SECONDS.between(now, authorization.getToken(OidcIdToken.class).getToken().getExpiresAt());
//			log.info("ID token expires in " + secondsUntilExpiry + " seconds");
			ttlSeconds = Math.max(ttlSeconds, secondsUntilExpiry + 60);
		}
		return ttlSeconds;
	}

	/**
	 * Removes the given {@link OAuth2Authorization} from Redis by its ID.
	 * <p>
	 * Called explicitly by {@link nidam.tokengenerator.config.RedisOIDCLogoutResponseHandler}
	 * during OIDC RP-initiated logout, since Spring Authorization Server does not call
	 * this method automatically during the standard logout flow.
	 * <p>
	 * @param authorization the authorization record to remove
	 */
	@Override
	public void remove(OAuth2Authorization authorization) {
		Assert.notNull(authorization, "authorization cannot be null");
		log.info("Removing authorization with id: " + authorization.getId());
		authorizationGrantAuthorizationRepository.deleteById(authorization.getId());
	}

	/**
	 * Looks up an {@link OAuth2Authorization} by its internal ID.
	 * <p>
	 * Used internally by Spring Authorization Server in scenarios where the full
	 * authorization record needs to be retrieved by its primary key rather than
	 * by a token value.
	 *
	 * @param id the internal authorization ID
	 * @return the matching {@link OAuth2Authorization}, or {@code null} if not found
	 */
	@Nullable
	@Override
	public OAuth2Authorization findById(String id) {
		Assert.hasText(id, "id cannot be empty");
		log.info("Finding authorization with id: " + id);
		return authorizationGrantAuthorizationRepository.findById(id)
				.map(this::toOAuth2Authorization)
				.orElse(null);
	}

	/**
	 * Looks up an {@link OAuth2Authorization} by a token value and optional token type.
	 * <p>
	 * Called by Spring Authorization Server at multiple points in the authorization
	 * code flow, including:
	 * <ul>
	 *     <li>During the token exchange — lookup by authorization code value</li>
	 *     <li>During token introspection — lookup by access token value</li>
	 *     <li>During OIDC logout — lookup by ID token value (via {@code id_token_hint})</li>
	 *     <li>During consent flow — lookup by {@code state} parameter</li>
	 * </ul>
	 * <p>
	 * When {@code tokenType} is {@code null}, all token types are searched in order:
	 * state/auth code, access/refresh token, ID token, device flow tokens.
	 *
	 * @param token     the raw token value to search for
	 * @param tokenType the type of token, or {@code null} to search all types
	 * @return the matching {@link OAuth2Authorization}, or {@code null} if not found
	 */
	@Nullable
	@Override
	public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
		Assert.hasText(token, "token cannot be empty");
		log.info("Finding authorization with token: " + token);
		OAuth2AuthorizationGrantAuthorization authorizationGrantAuthorization = null;
		if (tokenType == null) {
			authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByStateOrAuthorizationCode_TokenValue(token, token);
			if (authorizationGrantAuthorization == null) {
				authorizationGrantAuthorization =
						authorizationGrantAuthorizationRepository.findByAccessToken_TokenValueOrRefreshToken_TokenValue(token, token);
			}
			if (authorizationGrantAuthorization == null) {
				authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByIdToken_TokenValue(token);
			}
			if (authorizationGrantAuthorization == null) {
				authorizationGrantAuthorization =
						authorizationGrantAuthorizationRepository.findByDeviceStateOrDeviceCode_TokenValueOrUserCode_TokenValue(token, token, token);
			}
		}
		else if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
			authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByState(token);
			if (authorizationGrantAuthorization == null) {
				authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByDeviceState(token);
			}
		}
		else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
			authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByAuthorizationCode_TokenValue(token);
		}
		else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
			authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByAccessToken_TokenValue(token);
		}
		else if (OidcParameterNames.ID_TOKEN.equals(tokenType.getValue())) {
			authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByIdToken_TokenValue(token);
		}
		else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
			authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByRefreshToken_TokenValue(token);
		}
		else if (OAuth2ParameterNames.USER_CODE.equals(tokenType.getValue())) {
			authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByUserCode_TokenValue(token);
		}
		else if (OAuth2ParameterNames.DEVICE_CODE.equals(tokenType.getValue())) {
			authorizationGrantAuthorization = authorizationGrantAuthorizationRepository.findByDeviceCode_TokenValue(token);
		}
		return authorizationGrantAuthorization != null ? toOAuth2Authorization(authorizationGrantAuthorization) : null;
	}

	private OAuth2Authorization toOAuth2Authorization(OAuth2AuthorizationGrantAuthorization authorizationGrantAuthorization) {
		RegisteredClient registeredClient = registeredClientRepository.findById(authorizationGrantAuthorization.getRegisteredClientId());
		OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient);
		ModelMapper.mapOAuth2AuthorizationGrantAuthorization(authorizationGrantAuthorization, builder);
		return builder.build();
	}

}
