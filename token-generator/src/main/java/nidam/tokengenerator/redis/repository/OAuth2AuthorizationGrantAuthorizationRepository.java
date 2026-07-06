package nidam.tokengenerator.redis.repository;

import nidam.tokengenerator.redis.entity.OAuth2AuthorizationCodeGrantAuthorization;
import nidam.tokengenerator.redis.entity.OAuth2AuthorizationGrantAuthorization;
import nidam.tokengenerator.redis.entity.OAuth2DeviceCodeGrantAuthorization;
import nidam.tokengenerator.redis.entity.OidcAuthorizationCodeGrantAuthorization;

import nidam.tokengenerator.redis.service.RedisOAuth2AuthorizationService;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data Redis repository for {@link OAuth2AuthorizationGrantAuthorization} entities.
 *
 * <p>Provides finder methods used by
 * {@link RedisOAuth2AuthorizationService}
 * to look up authorization records by token values and other indexed fields.
 * Each finder method corresponds to a field annotated with
 * {@link org.springframework.data.redis.core.index.Indexed} on the entity or its
 * subclasses, which causes Spring Data Redis to maintain a secondary index
 * {@code SET} key for that field alongside the main {@code HASH} key.</p>
 *
 * <p>All finder methods use generics bounded by the entity hierarchy so that the
 * correct concrete subclass is returned without requiring an explicit cast at the
 * call site:</p>
 * <ul>
 *     <li>{@link OidcAuthorizationCodeGrantAuthorization} — OIDC authorization code flow
 *     (always used in Nidam since the client requests {@code openid} scope)</li>
 *     <li>{@link OAuth2AuthorizationCodeGrantAuthorization} — plain OAuth2 authorization
 *     code flow</li>
 *     <li>{@link OAuth2DeviceCodeGrantAuthorization} — device authorization flow</li>
 * </ul>
 *
 * @see RedisOAuth2AuthorizationService
 * @see OAuth2AuthorizationGrantAuthorization
 */
@Repository
public interface OAuth2AuthorizationGrantAuthorizationRepository extends CrudRepository<OAuth2AuthorizationGrantAuthorization, String> {

	/**
	 * Finds an authorization by the OAuth2 {@code state} parameter used during
	 * the authorization code flow to correlate the callback with the original request.
	 *
	 * @param state the state value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2AuthorizationCodeGrantAuthorization> T findByState(String state);

	/**
	 * Finds an authorization by the authorization code token value.
	 * Used during the token exchange step when the BFF posts the code to
	 * {@code /oauth2/token}.
	 *
	 * @param authorizationCode the authorization code value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2AuthorizationCodeGrantAuthorization> T findByAuthorizationCode_TokenValue(String authorizationCode);

	/**
	 * Finds an authorization by either the {@code state} parameter or the
	 * authorization code value. Used when the token type is unknown and both
	 * need to be checked in a single query.
	 *
	 * @param state             the state value
	 * @param authorizationCode the authorization code value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2AuthorizationCodeGrantAuthorization> T findByStateOrAuthorizationCode_TokenValue(String state, String authorizationCode);

	/**
	 * Finds an authorization by its access token value.
	 * Used during token introspection or resource server validation.
	 *
	 * @param accessToken the access token value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2AuthorizationGrantAuthorization> T findByAccessToken_TokenValue(String accessToken);

	/**
	 * Finds an authorization by its refresh token value.
	 * Currently unused in Nidam as refresh tokens are disabled.
	 *
	 * @param refreshToken the refresh token value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2AuthorizationGrantAuthorization> T findByRefreshToken_TokenValue(String refreshToken);

	/**
	 * Finds an authorization by either the access token or refresh token value.
	 * Used when the token type is unknown and both need to be checked in a single query.
	 *
	 * @param accessToken  the access token value
	 * @param refreshToken the refresh token value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2AuthorizationGrantAuthorization> T findByAccessToken_TokenValueOrRefreshToken_TokenValue(String accessToken, String refreshToken);

	/**
	 * Finds an authorization by its OIDC ID token value.
	 * Used during OIDC RP-initiated logout when the {@code id_token_hint} parameter
	 * is provided to identify the authorization record to remove from Redis.
	 *
	 * @param idToken the ID token value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OidcAuthorizationCodeGrantAuthorization> T findByIdToken_TokenValue(String idToken);

	/**
	 * Finds a device authorization by its device state value.
	 * Used during the device authorization flow. Not currently active in Nidam.
	 *
	 * @param deviceState the device state value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2DeviceCodeGrantAuthorization> T findByDeviceState(String deviceState);

	/**
	 * Finds a device authorization by its device code token value.
	 * Used during the device authorization flow. Not currently active in Nidam.
	 *
	 * @param deviceCode the device code value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2DeviceCodeGrantAuthorization> T findByDeviceCode_TokenValue(String deviceCode);

	/**
	 * Finds a device authorization by its user code token value.
	 * Used during the device authorization flow when the end user enters the
	 * short user code on a secondary device. Not currently active in Nidam.
	 *
	 * @param userCode the user code value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2DeviceCodeGrantAuthorization> T findByUserCode_TokenValue(String userCode);

	/**
	 * Finds a device authorization by device state, device code, or user code.
	 * Used when the token type is unknown and all device flow identifiers need
	 * to be checked in a single query. Not currently active in Nidam.
	 *
	 * @param deviceState the device state value
	 * @param deviceCode  the device code value
	 * @param userCode    the user code value
	 * @return the matching authorization, or {@code null} if not found
	 */
	<T extends OAuth2DeviceCodeGrantAuthorization> T findByDeviceStateOrDeviceCode_TokenValueOrUserCode_TokenValue(String deviceState, String deviceCode, String userCode);

}
