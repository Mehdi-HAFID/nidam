package nidam.tokengenerator.redis.service;

import java.security.Principal;

import nidam.tokengenerator.redis.entity.OAuth2AuthorizationCodeGrantAuthorization;
import nidam.tokengenerator.redis.entity.OAuth2AuthorizationGrantAuthorization;
import nidam.tokengenerator.redis.entity.OAuth2ClientCredentialsGrantAuthorization;
import nidam.tokengenerator.redis.entity.OAuth2DeviceCodeGrantAuthorization;
import nidam.tokengenerator.redis.entity.OAuth2RegisteredClient;
import nidam.tokengenerator.redis.entity.OAuth2TokenExchangeGrantAuthorization;
import nidam.tokengenerator.redis.entity.OAuth2UserConsent;
import nidam.tokengenerator.redis.entity.OidcAuthorizationCodeGrantAuthorization;

import nidam.tokengenerator.redis.repository.OAuth2UserConsentRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Internal utility class responsible for bidirectional conversion between Spring
 * Authorization Server's domain objects and their Redis entity counterparts.
 *
 * <p>This class is the bridge between two completely separate object models:</p>
 * <ul>
 *     <li><b>Spring domain objects</b> — {@link OAuth2Authorization},
 *     {@link org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent},
 *     {@link RegisteredClient} — owned by Spring Authorization Server, immutable,
 *     not serializable to Redis directly</li>
 *     <li><b>Redis entities</b> — {@link OAuth2AuthorizationGrantAuthorization},
 *     {@link OAuth2UserConsent}, {@link OAuth2RegisteredClient} — owned by Nidam,
 *     designed for Spring Data Redis {@link org.springframework.data.redis.core.RedisHash}
 *     persistence with flattened fields and {@link org.springframework.data.redis.core.index.Indexed}
 *     secondary indexes</li>
 * </ul>
 *
 * <h3>Conversion directions</h3>
 * <table border="1">
 *     <tr>
 *         <th>Method prefix</th>
 *         <th>Direction</th>
 *         <th>Called by</th>
 *     </tr>
 *     <tr>
 *         <td>{@code convert*}</td>
 *         <td>Spring domain → Redis entity</td>
 *         <td>{@link RedisOAuth2AuthorizationService#save}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code map*}</td>
 *         <td>Redis entity → Spring domain (via builder)</td>
 *         <td>{@link RedisOAuth2AuthorizationService#findById},
 *         {@link RedisOAuth2AuthorizationService#findByToken}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code extract*}</td>
 *         <td>Spring domain → Redis token entity (internal helpers)</td>
 *         <td>{@code convert*} methods</td>
 *     </tr>
 * </table>
 *
 * <h3>Grant type dispatch</h3>
 * <p>{@link #convertOAuth2AuthorizationGrantAuthorization} inspects the authorization
 * grant type and OIDC scope to select the correct concrete entity subclass:</p>
 * <ul>
 *     <li>Authorization code + {@code openid} scope →
 *     {@link OidcAuthorizationCodeGrantAuthorization}</li>
 *     <li>Authorization code without {@code openid} →
 *     {@link OAuth2AuthorizationCodeGrantAuthorization}</li>
 *     <li>Client credentials →
 *     {@link OAuth2ClientCredentialsGrantAuthorization}</li>
 *     <li>Device code →
 *     {@link OAuth2DeviceCodeGrantAuthorization}</li>
 *     <li>Token exchange →
 *     {@link OAuth2TokenExchangeGrantAuthorization}</li>
 * </ul>
 *
 * <p>In Nidam, only the OIDC authorization code path is active since the registered
 * client always requests the {@code openid} scope.</p>
 */
final class ModelMapper {

	/**
	 * Converts a Spring {@link RegisteredClient} to its Redis entity counterpart
	 * {@link OAuth2RegisteredClient}, including nested {@code ClientSettings} and
	 * {@code TokenSettings}.
	 *
	 * @param registeredClient the Spring registered client
	 * @return the Redis entity representation
	 */
	static OAuth2RegisteredClient convertOAuth2RegisteredClient(RegisteredClient registeredClient) {
		OAuth2RegisteredClient.ClientSettings clientSettings = new OAuth2RegisteredClient.ClientSettings(
				registeredClient.getClientSettings().isRequireProofKey(),
				registeredClient.getClientSettings().isRequireAuthorizationConsent(),
				registeredClient.getClientSettings().getJwkSetUrl(),
				registeredClient.getClientSettings().getTokenEndpointAuthenticationSigningAlgorithm(),
				registeredClient.getClientSettings().getX509CertificateSubjectDN());

		OAuth2RegisteredClient.TokenSettings tokenSettings = new OAuth2RegisteredClient.TokenSettings(
				registeredClient.getTokenSettings().getAuthorizationCodeTimeToLive(),
				registeredClient.getTokenSettings().getAccessTokenTimeToLive(),
				registeredClient.getTokenSettings().getAccessTokenFormat(),
				registeredClient.getTokenSettings().getDeviceCodeTimeToLive(),
				registeredClient.getTokenSettings().isReuseRefreshTokens(),
				registeredClient.getTokenSettings().getRefreshTokenTimeToLive(),
				registeredClient.getTokenSettings().getIdTokenSignatureAlgorithm(),
				registeredClient.getTokenSettings().isX509CertificateBoundAccessTokens());

		return new OAuth2RegisteredClient(registeredClient.getId(), registeredClient.getClientId(),
				registeredClient.getClientIdIssuedAt(), registeredClient.getClientSecret(),
				registeredClient.getClientSecretExpiresAt(), registeredClient.getClientName(),
				registeredClient.getClientAuthenticationMethods(), registeredClient.getAuthorizationGrantTypes(),
				registeredClient.getRedirectUris(), registeredClient.getPostLogoutRedirectUris(),
				registeredClient.getScopes(), clientSettings, tokenSettings);
	}

	/**
	 * Converts a Spring {@link OAuth2AuthorizationConsent} to its Redis entity
	 * counterpart {@link OAuth2UserConsent}.
	 * <p>
	 * The entity ID is constructed by concatenating the registered client ID and
	 * principal name, matching the composite key used by
	 * {@link OAuth2UserConsentRepository}.
	 *
	 * @param authorizationConsent the Spring authorization consent
	 * @return the Redis entity representation
	 */
	static OAuth2UserConsent convertOAuth2UserConsent(OAuth2AuthorizationConsent authorizationConsent) {
		String id = authorizationConsent.getRegisteredClientId()
				.concat("-")
				.concat(authorizationConsent.getPrincipalName());
		return new OAuth2UserConsent(id, authorizationConsent.getRegisteredClientId(),
				authorizationConsent.getPrincipalName(), authorizationConsent.getAuthorities());
	}

	/**
	 * Converts a Spring {@link OAuth2Authorization} to the appropriate Redis entity
	 * subclass based on the authorization grant type and requested scopes.
	 * <p>
	 * Returns {@code null} for unrecognized grant types.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis entity representation, or {@code null} if the grant type
	 *         is not supported
	 */
	static OAuth2AuthorizationGrantAuthorization convertOAuth2AuthorizationGrantAuthorization(OAuth2Authorization authorization) {

		if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(authorization.getAuthorizationGrantType())) {
			OAuth2AuthorizationRequest authorizationRequest = authorization.getAttribute(OAuth2AuthorizationRequest.class.getName());
			return authorizationRequest.getScopes().contains(OidcScopes.OPENID)
					? convertOidcAuthorizationCodeGrantAuthorization(authorization)
					: convertOAuth2AuthorizationCodeGrantAuthorization(authorization);
		} else if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(authorization.getAuthorizationGrantType())) {
			return convertOAuth2ClientCredentialsGrantAuthorization(authorization);
		} else if (AuthorizationGrantType.DEVICE_CODE.equals(authorization.getAuthorizationGrantType())) {
			return convertOAuth2DeviceCodeGrantAuthorization(authorization);
		} else if (AuthorizationGrantType.TOKEN_EXCHANGE.equals(authorization.getAuthorizationGrantType())) {
			return convertOAuth2TokenExchangeGrantAuthorization(authorization);
		}
		return null;
	}

	/**
	 * Converts an OIDC authorization code grant to
	 * {@link OidcAuthorizationCodeGrantAuthorization}, including the ID token.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis OIDC entity
	 */
	static OidcAuthorizationCodeGrantAuthorization convertOidcAuthorizationCodeGrantAuthorization(OAuth2Authorization authorization) {
		OAuth2AuthorizationCodeGrantAuthorization.AuthorizationCode authorizationCode = extractAuthorizationCode(
				authorization);
		OAuth2AuthorizationGrantAuthorization.AccessToken accessToken = extractAccessToken(authorization);
		OAuth2AuthorizationGrantAuthorization.RefreshToken refreshToken = extractRefreshToken(authorization);
		OidcAuthorizationCodeGrantAuthorization.IdToken idToken = extractIdToken(authorization);

		return new OidcAuthorizationCodeGrantAuthorization(authorization.getId(), authorization.getRegisteredClientId(),
				authorization.getPrincipalName(), authorization.getAuthorizedScopes(), accessToken, refreshToken,
				authorization.getAttribute(Principal.class.getName()),
				authorization.getAttribute(OAuth2AuthorizationRequest.class.getName()), authorizationCode,
				authorization.getAttribute(OAuth2ParameterNames.STATE), idToken);
	}

	/**
	 * Converts a plain OAuth2 authorization code grant (without OIDC) to
	 * {@link OAuth2AuthorizationCodeGrantAuthorization}.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis entity
	 */
	static OAuth2AuthorizationCodeGrantAuthorization convertOAuth2AuthorizationCodeGrantAuthorization(OAuth2Authorization authorization) {

		OAuth2AuthorizationCodeGrantAuthorization.AuthorizationCode authorizationCode = extractAuthorizationCode(
				authorization);
		OAuth2AuthorizationGrantAuthorization.AccessToken accessToken = extractAccessToken(authorization);
		OAuth2AuthorizationGrantAuthorization.RefreshToken refreshToken = extractRefreshToken(authorization);

		return new OAuth2AuthorizationCodeGrantAuthorization(authorization.getId(),
				authorization.getRegisteredClientId(), authorization.getPrincipalName(),
				authorization.getAuthorizedScopes(), accessToken, refreshToken,
				authorization.getAttribute(Principal.class.getName()),
				authorization.getAttribute(OAuth2AuthorizationRequest.class.getName()), authorizationCode,
				authorization.getAttribute(OAuth2ParameterNames.STATE));
	}

	/**
	 * Converts a client credentials grant to {@link OAuth2ClientCredentialsGrantAuthorization}.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis entity
	 */
	static OAuth2ClientCredentialsGrantAuthorization convertOAuth2ClientCredentialsGrantAuthorization(OAuth2Authorization authorization) {

		OAuth2AuthorizationGrantAuthorization.AccessToken accessToken = extractAccessToken(authorization);

		return new OAuth2ClientCredentialsGrantAuthorization(authorization.getId(),
				authorization.getRegisteredClientId(), authorization.getPrincipalName(),
				authorization.getAuthorizedScopes(), accessToken);
	}

	/**
	 * Converts a device code grant to {@link OAuth2DeviceCodeGrantAuthorization}.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis entity
	 */
	static OAuth2DeviceCodeGrantAuthorization convertOAuth2DeviceCodeGrantAuthorization(OAuth2Authorization authorization) {

		OAuth2AuthorizationGrantAuthorization.AccessToken accessToken = extractAccessToken(authorization);
		OAuth2AuthorizationGrantAuthorization.RefreshToken refreshToken = extractRefreshToken(authorization);
		OAuth2DeviceCodeGrantAuthorization.DeviceCode deviceCode = extractDeviceCode(authorization);
		OAuth2DeviceCodeGrantAuthorization.UserCode userCode = extractUserCode(authorization);

		return new OAuth2DeviceCodeGrantAuthorization(authorization.getId(), authorization.getRegisteredClientId(),
				authorization.getPrincipalName(), authorization.getAuthorizedScopes(), accessToken, refreshToken,
				authorization.getAttribute(Principal.class.getName()), deviceCode, userCode,
				authorization.getAttribute(OAuth2ParameterNames.SCOPE),
				authorization.getAttribute(OAuth2ParameterNames.STATE));
	}

	/**
	 * Converts a token exchange grant to {@link OAuth2TokenExchangeGrantAuthorization}.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis entity
	 */
	static OAuth2TokenExchangeGrantAuthorization convertOAuth2TokenExchangeGrantAuthorization(OAuth2Authorization authorization) {

		OAuth2AuthorizationGrantAuthorization.AccessToken accessToken = extractAccessToken(authorization);

		return new OAuth2TokenExchangeGrantAuthorization(authorization.getId(), authorization.getRegisteredClientId(),
				authorization.getPrincipalName(), authorization.getAuthorizedScopes(), accessToken);
	}

	/**
	 * Extracts the authorization code from an {@link OAuth2Authorization} and wraps it
	 * in the Redis entity type {@link OAuth2AuthorizationCodeGrantAuthorization.AuthorizationCode}.
	 * Returns {@code null} if no authorization code is present.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis authorization code entity, or {@code null}
	 */
	static OAuth2AuthorizationCodeGrantAuthorization.AuthorizationCode extractAuthorizationCode(OAuth2Authorization authorization) {
		OAuth2AuthorizationCodeGrantAuthorization.AuthorizationCode authorizationCode = null;
		if (authorization.getToken(OAuth2AuthorizationCode.class) != null) {
			OAuth2Authorization.Token<OAuth2AuthorizationCode> oauth2AuthorizationCode = authorization
					.getToken(OAuth2AuthorizationCode.class);
			authorizationCode = new OAuth2AuthorizationCodeGrantAuthorization.AuthorizationCode(
					oauth2AuthorizationCode.getToken().getTokenValue(),
					oauth2AuthorizationCode.getToken().getIssuedAt(), oauth2AuthorizationCode.getToken().getExpiresAt(),
					oauth2AuthorizationCode.isInvalidated());
		}
		return authorizationCode;
	}

	/**
	 * Extracts the access token from an {@link OAuth2Authorization} and wraps it
	 * in the Redis entity type {@link OAuth2AuthorizationGrantAuthorization.AccessToken},
	 * including token format metadata and JWT claims.
	 * Returns {@code null} if no access token is present.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis access token entity, or {@code null}
	 */
	static OAuth2AuthorizationGrantAuthorization.AccessToken extractAccessToken(OAuth2Authorization authorization) {
		OAuth2AuthorizationGrantAuthorization.AccessToken accessToken = null;
		if (authorization.getAccessToken() != null) {
			OAuth2Authorization.Token<OAuth2AccessToken> oauth2AccessToken = authorization.getAccessToken();
			OAuth2TokenFormat tokenFormat = null;
			if (OAuth2TokenFormat.SELF_CONTAINED.getValue()
					.equals(oauth2AccessToken.getMetadata(OAuth2TokenFormat.class.getName()))) {
				tokenFormat = OAuth2TokenFormat.SELF_CONTAINED;
			} else if (OAuth2TokenFormat.REFERENCE.getValue()
					.equals(oauth2AccessToken.getMetadata(OAuth2TokenFormat.class.getName()))) {
				tokenFormat = OAuth2TokenFormat.REFERENCE;
			}
			accessToken = new OAuth2AuthorizationGrantAuthorization.AccessToken(
					oauth2AccessToken.getToken().getTokenValue(), oauth2AccessToken.getToken().getIssuedAt(),
					oauth2AccessToken.getToken().getExpiresAt(), oauth2AccessToken.isInvalidated(),
					oauth2AccessToken.getToken().getTokenType(), oauth2AccessToken.getToken().getScopes(), tokenFormat,
					new OAuth2AuthorizationGrantAuthorization.ClaimsHolder(oauth2AccessToken.getClaims()));
		}
		return accessToken;
	}

	/**
	 * Extracts the refresh token from an {@link OAuth2Authorization} and wraps it
	 * in the Redis entity type {@link OAuth2AuthorizationGrantAuthorization.RefreshToken}.
	 * Returns {@code null} if no refresh token is present.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis refresh token entity, or {@code null}
	 */
	static OAuth2AuthorizationGrantAuthorization.RefreshToken extractRefreshToken(OAuth2Authorization authorization) {
		OAuth2AuthorizationGrantAuthorization.RefreshToken refreshToken = null;
		if (authorization.getRefreshToken() != null) {
			OAuth2Authorization.Token<OAuth2RefreshToken> oauth2RefreshToken = authorization.getRefreshToken();
			refreshToken = new OAuth2AuthorizationGrantAuthorization.RefreshToken(
					oauth2RefreshToken.getToken().getTokenValue(), oauth2RefreshToken.getToken().getIssuedAt(),
					oauth2RefreshToken.getToken().getExpiresAt(), oauth2RefreshToken.isInvalidated());
		}
		return refreshToken;
	}

	/**
	 * Extracts the OIDC ID token from an {@link OAuth2Authorization} and wraps it
	 * in the Redis entity type {@link OidcAuthorizationCodeGrantAuthorization.IdToken},
	 * including JWT claims.
	 * Returns {@code null} if no ID token is present.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis ID token entity, or {@code null}
	 */
	static OidcAuthorizationCodeGrantAuthorization.IdToken extractIdToken(OAuth2Authorization authorization) {
		OidcAuthorizationCodeGrantAuthorization.IdToken idToken = null;
		if (authorization.getToken(OidcIdToken.class) != null) {
			OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
			idToken = new OidcAuthorizationCodeGrantAuthorization.IdToken(oidcIdToken.getToken().getTokenValue(),
					oidcIdToken.getToken().getIssuedAt(), oidcIdToken.getToken().getExpiresAt(),
					oidcIdToken.isInvalidated(),
					new OAuth2AuthorizationGrantAuthorization.ClaimsHolder(oidcIdToken.getClaims()));
		}
		return idToken;
	}

	/**
	 * Extracts the device code from an {@link OAuth2Authorization} and wraps it
	 * in the Redis entity type {@link OAuth2DeviceCodeGrantAuthorization.DeviceCode}.
	 * Returns {@code null} if no device code is present.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis device code entity, or {@code null}
	 */
	static OAuth2DeviceCodeGrantAuthorization.DeviceCode extractDeviceCode(OAuth2Authorization authorization) {
		OAuth2DeviceCodeGrantAuthorization.DeviceCode deviceCode = null;
		if (authorization.getToken(OAuth2DeviceCode.class) != null) {
			OAuth2Authorization.Token<OAuth2DeviceCode> oauth2DeviceCode = authorization
					.getToken(OAuth2DeviceCode.class);
			deviceCode = new OAuth2DeviceCodeGrantAuthorization.DeviceCode(oauth2DeviceCode.getToken().getTokenValue(),
					oauth2DeviceCode.getToken().getIssuedAt(), oauth2DeviceCode.getToken().getExpiresAt(),
					oauth2DeviceCode.isInvalidated());
		}
		return deviceCode;
	}

	/**
	 * Extracts the user code from an {@link OAuth2Authorization} and wraps it
	 * in the Redis entity type {@link OAuth2DeviceCodeGrantAuthorization.UserCode}.
	 * Returns {@code null} if no user code is present.
	 *
	 * @param authorization the Spring authorization
	 * @return the Redis user code entity, or {@code null}
	 */
	static OAuth2DeviceCodeGrantAuthorization.UserCode extractUserCode(OAuth2Authorization authorization) {
		OAuth2DeviceCodeGrantAuthorization.UserCode userCode = null;
		if (authorization.getToken(OAuth2UserCode.class) != null) {
			OAuth2Authorization.Token<OAuth2UserCode> oauth2UserCode = authorization.getToken(OAuth2UserCode.class);
			userCode = new OAuth2DeviceCodeGrantAuthorization.UserCode(oauth2UserCode.getToken().getTokenValue(),
					oauth2UserCode.getToken().getIssuedAt(), oauth2UserCode.getToken().getExpiresAt(),
					oauth2UserCode.isInvalidated());
		}
		return userCode;
	}

	/**
	 * Converts a Redis {@link OAuth2RegisteredClient} entity back to a Spring
	 * {@link RegisteredClient}, reconstructing {@code ClientSettings} and
	 * {@code TokenSettings} from their flattened Redis representations.
	 * Null-safe for optional settings fields.
	 *
	 * @param oauth2RegisteredClient the Redis registered client entity
	 * @return the Spring registered client
	 */
	static RegisteredClient convertRegisteredClient(OAuth2RegisteredClient oauth2RegisteredClient) {
		ClientSettings.Builder clientSettingsBuilder = ClientSettings.builder()
				.requireProofKey(oauth2RegisteredClient.getClientSettings().isRequireProofKey())
				.requireAuthorizationConsent(oauth2RegisteredClient.getClientSettings().isRequireAuthorizationConsent());
		if (StringUtils.hasText(oauth2RegisteredClient.getClientSettings().getJwkSetUrl())) {
			clientSettingsBuilder.jwkSetUrl(oauth2RegisteredClient.getClientSettings().getJwkSetUrl());
		}
		if (oauth2RegisteredClient.getClientSettings().getTokenEndpointAuthenticationSigningAlgorithm() != null) {
			clientSettingsBuilder.tokenEndpointAuthenticationSigningAlgorithm(
					oauth2RegisteredClient.getClientSettings().getTokenEndpointAuthenticationSigningAlgorithm());
		}
		if (StringUtils.hasText(oauth2RegisteredClient.getClientSettings().getX509CertificateSubjectDN())) {
			clientSettingsBuilder
					.x509CertificateSubjectDN(oauth2RegisteredClient.getClientSettings().getX509CertificateSubjectDN());
		}
		ClientSettings clientSettings = clientSettingsBuilder.build();

		TokenSettings.Builder tokenSettingsBuilder = TokenSettings.builder();
		if (oauth2RegisteredClient.getTokenSettings().getAuthorizationCodeTimeToLive() != null) {
			tokenSettingsBuilder.authorizationCodeTimeToLive(oauth2RegisteredClient.getTokenSettings().getAuthorizationCodeTimeToLive());
		}
		if (oauth2RegisteredClient.getTokenSettings().getAccessTokenTimeToLive() != null) {
			tokenSettingsBuilder.accessTokenTimeToLive(oauth2RegisteredClient.getTokenSettings().getAccessTokenTimeToLive());
		}
		if (oauth2RegisteredClient.getTokenSettings().getAccessTokenFormat() != null) {
			tokenSettingsBuilder.accessTokenFormat(oauth2RegisteredClient.getTokenSettings().getAccessTokenFormat());
		}
		if (oauth2RegisteredClient.getTokenSettings().getDeviceCodeTimeToLive() != null) {
			tokenSettingsBuilder.deviceCodeTimeToLive(oauth2RegisteredClient.getTokenSettings().getDeviceCodeTimeToLive());
		}
		tokenSettingsBuilder.reuseRefreshTokens(oauth2RegisteredClient.getTokenSettings().isReuseRefreshTokens());
		if (oauth2RegisteredClient.getTokenSettings().getRefreshTokenTimeToLive() != null) {
			tokenSettingsBuilder.refreshTokenTimeToLive(oauth2RegisteredClient.getTokenSettings().getRefreshTokenTimeToLive());
		}
		if (oauth2RegisteredClient.getTokenSettings().getIdTokenSignatureAlgorithm() != null) {
			tokenSettingsBuilder.idTokenSignatureAlgorithm(oauth2RegisteredClient.getTokenSettings().getIdTokenSignatureAlgorithm());
		}
		tokenSettingsBuilder.x509CertificateBoundAccessTokens(oauth2RegisteredClient.getTokenSettings().isX509CertificateBoundAccessTokens());
		TokenSettings tokenSettings = tokenSettingsBuilder.build();

		RegisteredClient.Builder registeredClientBuilder = RegisteredClient.withId(oauth2RegisteredClient.getId())
				.clientId(oauth2RegisteredClient.getClientId())
				.clientIdIssuedAt(oauth2RegisteredClient.getClientIdIssuedAt())
				.clientSecret(oauth2RegisteredClient.getClientSecret())
				.clientSecretExpiresAt(oauth2RegisteredClient.getClientSecretExpiresAt())
				.clientName(oauth2RegisteredClient.getClientName())
				.clientAuthenticationMethods((clientAuthenticationMethods) -> clientAuthenticationMethods
						.addAll(oauth2RegisteredClient.getClientAuthenticationMethods()))
				.authorizationGrantTypes((authorizationGrantTypes) -> authorizationGrantTypes
						.addAll(oauth2RegisteredClient.getAuthorizationGrantTypes()))
				.clientSettings(clientSettings)
				.tokenSettings(tokenSettings);
		if (!CollectionUtils.isEmpty(oauth2RegisteredClient.getRedirectUris())) {
			registeredClientBuilder.redirectUris((redirectUris) -> redirectUris.addAll(oauth2RegisteredClient.getRedirectUris()));
		}
		if (!CollectionUtils.isEmpty(oauth2RegisteredClient.getPostLogoutRedirectUris())) {
			registeredClientBuilder.postLogoutRedirectUris((postLogoutRedirectUris) ->
					postLogoutRedirectUris.addAll(oauth2RegisteredClient.getPostLogoutRedirectUris()));
		}
		if (!CollectionUtils.isEmpty(oauth2RegisteredClient.getScopes())) {
			registeredClientBuilder.scopes((scopes) -> scopes.addAll(oauth2RegisteredClient.getScopes()));
		}

		return registeredClientBuilder.build();
	}

	/**
	 * Converts a Redis {@link OAuth2UserConsent} entity back to a Spring
	 * {@link OAuth2AuthorizationConsent}.
	 *
	 * @param userConsent the Redis consent entity
	 * @return the Spring authorization consent
	 */
	static OAuth2AuthorizationConsent convertOAuth2AuthorizationConsent(OAuth2UserConsent userConsent) {
		return OAuth2AuthorizationConsent.withId(userConsent.getRegisteredClientId(), userConsent.getPrincipalName())
				.authorities((authorities) -> authorities.addAll(userConsent.getAuthorities()))
				.build();
	}

	/**
	 * Dispatches mapping from a Redis {@link OAuth2AuthorizationGrantAuthorization}
	 * entity to the provided {@link OAuth2Authorization.Builder} based on the
	 * concrete entity subclass. Called during {@code findById} and {@code findByToken}
	 * to reconstruct the Spring domain object from Redis.
	 *
	 * @param authorizationGrantAuthorization the Redis entity
	 * @param builder                         the Spring authorization builder to populate
	 */
	static void mapOAuth2AuthorizationGrantAuthorization(OAuth2AuthorizationGrantAuthorization authorizationGrantAuthorization,
														 OAuth2Authorization.Builder builder) {

		if (authorizationGrantAuthorization instanceof OidcAuthorizationCodeGrantAuthorization authorizationGrant) {
			mapOidcAuthorizationCodeGrantAuthorization(authorizationGrant, builder);
		} else if (authorizationGrantAuthorization instanceof OAuth2AuthorizationCodeGrantAuthorization authorizationGrant) {
			mapOAuth2AuthorizationCodeGrantAuthorization(authorizationGrant, builder);
		} else if (authorizationGrantAuthorization instanceof OAuth2ClientCredentialsGrantAuthorization authorizationGrant) {
			mapOAuth2ClientCredentialsGrantAuthorization(authorizationGrant, builder);
		} else if (authorizationGrantAuthorization instanceof OAuth2DeviceCodeGrantAuthorization authorizationGrant) {
			mapOAuth2DeviceCodeGrantAuthorization(authorizationGrant, builder);
		} else if (authorizationGrantAuthorization instanceof OAuth2TokenExchangeGrantAuthorization authorizationGrant) {
			mapOAuth2TokenExchangeGrantAuthorization(authorizationGrant, builder);
		}
	}

	/**
	 * Maps an {@link OidcAuthorizationCodeGrantAuthorization} Redis entity to the
	 * builder, delegating to {@link #mapOAuth2AuthorizationCodeGrantAuthorization}
	 * for the common fields and then adding the ID token.
	 *
	 * @param authorizationCodeGrantAuthorization the Redis OIDC entity
	 * @param builder                             the Spring authorization builder
	 */
	static void mapOidcAuthorizationCodeGrantAuthorization(OidcAuthorizationCodeGrantAuthorization authorizationCodeGrantAuthorization,
														   OAuth2Authorization.Builder builder) {

		mapOAuth2AuthorizationCodeGrantAuthorization(authorizationCodeGrantAuthorization, builder);
		mapIdToken(authorizationCodeGrantAuthorization.getIdToken(), builder);
	}

	/**
	 * Maps an {@link OAuth2AuthorizationCodeGrantAuthorization} Redis entity to the
	 * builder, setting grant type, principal, scopes, state, authorization code,
	 * access token, and refresh token.
	 *
	 * @param authorizationCodeGrantAuthorization the Redis entity
	 * @param builder                             the Spring authorization builder
	 */
	static void mapOAuth2AuthorizationCodeGrantAuthorization(OAuth2AuthorizationCodeGrantAuthorization authorizationCodeGrantAuthorization,
															 OAuth2Authorization.Builder builder) {

		builder.id(authorizationCodeGrantAuthorization.getId())
				.principalName(authorizationCodeGrantAuthorization.getPrincipalName())
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizedScopes(authorizationCodeGrantAuthorization.getAuthorizedScopes())
				.attribute(Principal.class.getName(), authorizationCodeGrantAuthorization.getPrincipal())
				.attribute(OAuth2AuthorizationRequest.class.getName(),
						authorizationCodeGrantAuthorization.getAuthorizationRequest());
		if (StringUtils.hasText(authorizationCodeGrantAuthorization.getState())) {
			builder.attribute(OAuth2ParameterNames.STATE, authorizationCodeGrantAuthorization.getState());
		}

		mapAuthorizationCode(authorizationCodeGrantAuthorization.getAuthorizationCode(), builder);
		mapAccessToken(authorizationCodeGrantAuthorization.getAccessToken(), builder);
		mapRefreshToken(authorizationCodeGrantAuthorization.getRefreshToken(), builder);
	}

	/**
	 * Maps an {@link OAuth2ClientCredentialsGrantAuthorization} Redis entity to
	 * the builder.
	 *
	 * @param clientCredentialsGrantAuthorization the Redis entity
	 * @param builder                             the Spring authorization builder
	 */
	static void mapOAuth2ClientCredentialsGrantAuthorization(OAuth2ClientCredentialsGrantAuthorization clientCredentialsGrantAuthorization,
															 OAuth2Authorization.Builder builder) {

		builder.id(clientCredentialsGrantAuthorization.getId())
				.principalName(clientCredentialsGrantAuthorization.getPrincipalName())
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.authorizedScopes(clientCredentialsGrantAuthorization.getAuthorizedScopes());

		mapAccessToken(clientCredentialsGrantAuthorization.getAccessToken(), builder);
	}

	/**
	 * Maps an {@link OAuth2DeviceCodeGrantAuthorization} Redis entity to the builder,
	 * including device code, user code, and optional principal and scope attributes.
	 *
	 * @param deviceCodeGrantAuthorization the Redis entity
	 * @param builder                      the Spring authorization builder
	 */
	static void mapOAuth2DeviceCodeGrantAuthorization(OAuth2DeviceCodeGrantAuthorization deviceCodeGrantAuthorization,
	                                                  OAuth2Authorization.Builder builder) {

		builder.id(deviceCodeGrantAuthorization.getId())
				.principalName(deviceCodeGrantAuthorization.getPrincipalName())
				.authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
				.authorizedScopes(deviceCodeGrantAuthorization.getAuthorizedScopes());
		if (deviceCodeGrantAuthorization.getPrincipal() != null) {
			builder.attribute(Principal.class.getName(), deviceCodeGrantAuthorization.getPrincipal());
		}
		if (deviceCodeGrantAuthorization.getRequestedScopes() != null) {
			builder.attribute(OAuth2ParameterNames.SCOPE, deviceCodeGrantAuthorization.getRequestedScopes());
		}
		if (StringUtils.hasText(deviceCodeGrantAuthorization.getDeviceState())) {
			builder.attribute(OAuth2ParameterNames.STATE, deviceCodeGrantAuthorization.getDeviceState());
		}

		mapAccessToken(deviceCodeGrantAuthorization.getAccessToken(), builder);
		mapRefreshToken(deviceCodeGrantAuthorization.getRefreshToken(), builder);
		mapDeviceCode(deviceCodeGrantAuthorization.getDeviceCode(), builder);
		mapUserCode(deviceCodeGrantAuthorization.getUserCode(), builder);
	}

	/**
	 * Maps an {@link OAuth2TokenExchangeGrantAuthorization} Redis entity to the builder.
	 *
	 * @param tokenExchangeGrantAuthorization the Redis entity
	 * @param builder                         the Spring authorization builder
	 */
	static void mapOAuth2TokenExchangeGrantAuthorization(OAuth2TokenExchangeGrantAuthorization tokenExchangeGrantAuthorization,
														 OAuth2Authorization.Builder builder) {

		builder.id(tokenExchangeGrantAuthorization.getId())
				.principalName(tokenExchangeGrantAuthorization.getPrincipalName())
				.authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
				.authorizedScopes(tokenExchangeGrantAuthorization.getAuthorizedScopes());

		mapAccessToken(tokenExchangeGrantAuthorization.getAccessToken(), builder);
	}

	/**
	 * Reconstructs a Spring {@link OAuth2AuthorizationCode} from its Redis entity
	 * representation and adds it to the builder with invalidation metadata.
	 * No-op if {@code authorizationCode} is {@code null}.
	 *
	 * @param authorizationCode the Redis authorization code entity
	 * @param builder           the Spring authorization builder
	 */
	static void mapAuthorizationCode(OAuth2AuthorizationCodeGrantAuthorization.AuthorizationCode authorizationCode,
	                                 OAuth2Authorization.Builder builder) {
		if (authorizationCode == null) {
			return;
		}
		OAuth2AuthorizationCode oauth2AuthorizationCode = new OAuth2AuthorizationCode(authorizationCode.getTokenValue(),
				authorizationCode.getIssuedAt(), authorizationCode.getExpiresAt());
		builder.token(oauth2AuthorizationCode, (metadata) -> metadata
				.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, authorizationCode.isInvalidated()));
	}

	/**
	 * Reconstructs a Spring {@link OAuth2AccessToken} from its Redis entity
	 * representation and adds it to the builder with invalidation metadata,
	 * JWT claims, and token format metadata.
	 * No-op if {@code accessToken} is {@code null}.
	 *
	 * @param accessToken the Redis access token entity
	 * @param builder     the Spring authorization builder
	 */
	static void mapAccessToken(OAuth2AuthorizationGrantAuthorization.AccessToken accessToken, OAuth2Authorization.Builder builder) {
		if (accessToken == null) {
			return;
		}
		OAuth2AccessToken oauth2AccessToken = new OAuth2AccessToken(accessToken.getTokenType(), accessToken.getTokenValue(),
				accessToken.getIssuedAt(), accessToken.getExpiresAt(), accessToken.getScopes());
		builder.token(oauth2AccessToken, (metadata) -> {
			metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, accessToken.isInvalidated());
			metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, accessToken.getClaims().getClaims());
			metadata.put(OAuth2TokenFormat.class.getName(), accessToken.getTokenFormat().getValue());
		});
	}

	/**
	 * Reconstructs a Spring {@link OAuth2RefreshToken} from its Redis entity
	 * representation and adds it to the builder with invalidation metadata.
	 * No-op if {@code refreshToken} is {@code null}.
	 *
	 * @param refreshToken the Redis refresh token entity
	 * @param builder      the Spring authorization builder
	 */
	static void mapRefreshToken(OAuth2AuthorizationGrantAuthorization.RefreshToken refreshToken, OAuth2Authorization.Builder builder) {
		if (refreshToken == null) {
			return;
		}
		OAuth2RefreshToken oauth2RefreshToken = new OAuth2RefreshToken(refreshToken.getTokenValue(), refreshToken.getIssuedAt(),
				refreshToken.getExpiresAt());
		builder.token(oauth2RefreshToken, (metadata) ->
				metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, refreshToken.isInvalidated())
		);
	}

	/**
	 * Reconstructs a Spring {@link OidcIdToken} from its Redis entity representation
	 * and adds it to the builder with invalidation metadata and JWT claims.
	 * No-op if {@code idToken} is {@code null}.
	 *
	 * @param idToken the Redis ID token entity
	 * @param builder the Spring authorization builder
	 */
	static void mapIdToken(OidcAuthorizationCodeGrantAuthorization.IdToken idToken, OAuth2Authorization.Builder builder) {
		if (idToken == null) {
			return;
		}
		OidcIdToken oidcIdToken = new OidcIdToken(idToken.getTokenValue(), idToken.getIssuedAt(), idToken.getExpiresAt(),
				idToken.getClaims().getClaims());
		builder.token(oidcIdToken, (metadata) -> {
			metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, idToken.isInvalidated());
			metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, idToken.getClaims().getClaims());
		});
	}

	/**
	 * Reconstructs a Spring {@link OAuth2DeviceCode} from its Redis entity
	 * representation and adds it to the builder with invalidation metadata.
	 * No-op if {@code deviceCode} is {@code null}.
	 *
	 * @param deviceCode the Redis device code entity
	 * @param builder    the Spring authorization builder
	 */
	static void mapDeviceCode(OAuth2DeviceCodeGrantAuthorization.DeviceCode deviceCode, OAuth2Authorization.Builder builder) {
		if (deviceCode == null) {
			return;
		}
		OAuth2DeviceCode oauth2DeviceCode = new OAuth2DeviceCode(deviceCode.getTokenValue(), deviceCode.getIssuedAt(), deviceCode.getExpiresAt());
		builder.token(oauth2DeviceCode, (metadata) ->
				metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, deviceCode.isInvalidated())
		);
	}

	/**
	 * Reconstructs a Spring {@link OAuth2UserCode} from its Redis entity
	 * representation and adds it to the builder with invalidation metadata.
	 * No-op if {@code userCode} is {@code null}.
	 *
	 * @param userCode the Redis user code entity
	 * @param builder  the Spring authorization builder
	 */
	static void mapUserCode(OAuth2DeviceCodeGrantAuthorization.UserCode userCode, OAuth2Authorization.Builder builder) {
		if (userCode == null) {
			return;
		}
		OAuth2UserCode oauth2UserCode = new OAuth2UserCode(userCode.getTokenValue(), userCode.getIssuedAt(), userCode.getExpiresAt());
		builder.token(oauth2UserCode, (metadata) ->
				metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, userCode.isInvalidated())
		);
	}

}
