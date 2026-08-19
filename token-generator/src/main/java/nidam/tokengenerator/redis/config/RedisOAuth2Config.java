package nidam.tokengenerator.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import nidam.tokengenerator.model.NidamUserDetails;
import nidam.tokengenerator.redis.convert.*;
import nidam.tokengenerator.redis.entity.OAuth2AuthorizationGrantAuthorization;
import nidam.tokengenerator.redis.repository.OAuth2AuthorizationGrantAuthorizationRepository;
import nidam.tokengenerator.redis.repository.OAuth2UserConsentRepository;
import nidam.tokengenerator.redis.service.RedisOAuth2AuthorizationConsentService;
import nidam.tokengenerator.redis.service.RedisOAuth2AuthorizationService;
import org.springframework.boot.actuate.data.redis.RedisHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.convert.RedisCustomConversions;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

import java.util.Arrays;

/**
 * Configures the Redis infrastructure required for OAuth2 authorization state persistence
 * in high availability deployments of the token generator.
 *
 * <p>Active only when {@code nidam.session-mode=redis}. Works alongside
 * {@link RedisSessionConfig}, but the two classes serve entirely different concerns:</p>
 *
 * <table border="1">
 *     <tr>
 *         <th></th>
 *         <th>{@link RedisSessionConfig}</th>
 *         <th>{@link RedisOAuth2Config}</th>
 *     </tr>
 *     <tr>
 *         <td><b>Stores</b></td>
 *         <td>HTTP sessions (cookies, security context, saved requests)</td>
 *         <td>OAuth2 authorization records (codes, access tokens, ID tokens, consents)</td>
 *     </tr>
 *     <tr>
 *         <td><b>Redis key namespace</b></td>
 *         <td>{@code nidam:token-generator:sessions:*}</td>
 *         <td>{@code nidam:token-generator:oauth2:authorization:*}</td>
 *     </tr>
 *     <tr>
 *         <td><b>Serialization</b></td>
 *         <td>{@link org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer}
 *         with default typing</td>
 *         <td>Spring Data Redis {@link org.springframework.data.redis.core.RedisHash}
 *         flattening + custom byte converters for complex types</td>
 *     </tr>
 *     <tr>
 *         <td><b>Spring infrastructure</b></td>
 *         <td>Spring Session ({@code @EnableRedisHttpSession})</td>
 *         <td>Spring Data Redis ({@code @EnableRedisRepositories})</td>
 *     </tr>
 * </table>
 *
 * <h3>Why this class is needed for HA</h3>
 * <p>By default, Spring Authorization Server uses
 * {@link org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService},
 * which stores authorization codes and tokens in the JVM heap of a single instance.
 * With two token generator instances behind a load balancer, an authorization code
 * issued by instance A would not be found by instance B during the token exchange,
 * producing an {@code invalid_grant} error. This class replaces the in-memory service
 * with a shared Redis-backed implementation visible to all instances.</p>
 *
 * <h3>What this class configures</h3>
 * <ul>
 *     <li>A dedicated {@link com.fasterxml.jackson.databind.ObjectMapper} for the
 *     {@link org.springframework.data.redis.core.RedisHash} byte converters, separate
 *     from the session serializer's mapper</li>
 *     <li>Custom {@link org.springframework.data.redis.core.convert.RedisCustomConversions}
 *     for types that cannot be automatically flattened by Spring Data Redis</li>
 *     <li>A {@link RedisOAuth2AuthorizationService}
 *     backed by a Spring Data Redis repository</li>
 *     <li>A {@link RedisOAuth2AuthorizationConsentService}
 *     backed by a Spring Data Redis repository</li>
 * </ul>
 *
 * @see RedisSessionConfig
 * @see RedisOAuth2AuthorizationService
 * @see RedisOAuth2AuthorizationConsentService
 */
@Configuration(proxyBeanMethods = false)
@EnableRedisRepositories(basePackages = "nidam.tokengenerator.redis.repository",
		enableKeyspaceEvents = RedisKeyValueAdapter.EnableKeyspaceEvents.ON_STARTUP)    // <1>
@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "redis")
public class RedisOAuth2Config {

	/**
	 * Creates an {@link ObjectMapper} dedicated exclusively to the custom Redis
	 * byte converters used by Spring Data Redis {@link org.springframework.data.redis.core.RedisHash}
	 * repositories.
	 * <p>
	 * This mapper is intentionally separate from the Spring Session serializer's
	 * {@link ObjectMapper}. While the session serializer uses
	 * {@link com.fasterxml.jackson.databind.ObjectMapper#activateDefaultTyping default typing}
	 * to embed {@code @class} metadata into every JSON value, this mapper relies on
	 * Spring Security's built-in Jackson modules and explicit mixins to handle polymorphic
	 * types. The two mappers operate on completely independent Redis key namespaces and
	 * serialization pipelines.
	 * <p>
	 * Registers the following:
	 * <ul>
	 *     <li>Standard Spring Security Jackson modules via {@link SecurityJackson2Modules}</li>
	 *     <li>{@link OAuth2AuthorizationServerJackson2Module} for Authorization Server types</li>
	 *     <li>{@link NidamUserDetailsMixin} to control serialization of the custom principal,
	 *     most notably to prevent the password hash from being written to Redis</li>
	 * </ul>
	 *
	 * @return a configured {@link ObjectMapper} for use by the Redis byte converters
	 */
	@Bean
	public ObjectMapper redisConverterObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
		mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
		mapper.addMixIn(NidamUserDetails.class, NidamUserDetailsMixin.class);
		mapper.addMixIn(OAuth2AuthorizationGrantAuthorization.ClaimsHolder.class, ClaimsHolderMixin.class);

		return mapper;
	}

	/**
	 * Registers custom Spring Data Redis converters for complex Spring Security and
	 * OAuth2 types that cannot be automatically flattened by the
	 * {@link org.springframework.data.redis.core.convert.MappingRedisConverter}.
	 * <p>
	 * Spring Data Redis serializes {@link org.springframework.data.redis.core.RedisHash}
	 * entities by flattening their fields into dot-notation hash entries. However, certain
	 * types — such as {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken}
	 * and {@link org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest} —
	 * are polymorphic or too structurally complex for automatic flattening. These converters
	 * handle bidirectional serialization of those types to and from {@code byte[]}.
	 * <p>
	 * Each converter pair uses the shared {@link #redisConverterObjectMapper()} to ensure
	 * consistent type handling, including the {@link NidamUserDetailsMixin} that prevents
	 * the password hash from being persisted.
	 *
	 * @param redisConverterObjectMapper the shared {@link ObjectMapper} injected into converters
	 *                                   that require custom type handling
	 * @return a {@link RedisCustomConversions} instance registered with all required converters
	 */
	@Bean
	public RedisCustomConversions redisCustomConversions(ObjectMapper redisConverterObjectMapper) {
		return new RedisCustomConversions(Arrays.asList(
				new UsernamePasswordAuthenticationTokenToBytesConverter(redisConverterObjectMapper),
				new BytesToUsernamePasswordAuthenticationTokenConverter(redisConverterObjectMapper),
				new OAuth2AuthorizationRequestToBytesConverter(redisConverterObjectMapper),
				new BytesToOAuth2AuthorizationRequestConverter(redisConverterObjectMapper),
				new ClaimsHolderToBytesConverter(redisConverterObjectMapper),
				new BytesToClaimsHolderConverter(redisConverterObjectMapper)));
	}

	/**
	 * Provides a Redis-backed {@link org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService} that stores OAuth2
	 * authorization records — including authorization codes, access tokens, and ID tokens —
	 * in Redis instead of in-memory.
	 * <p>
	 * This bean replaces the default {@link org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService}
	 * and is required for high availability deployments where multiple token generator
	 * instances must share authorization state. Without a shared store, an authorization
	 * code issued by one instance would not be found by another instance during the
	 * token exchange, resulting in {@code invalid_grant} errors.
	 * <p>
	 * A {@link org.springframework.data.redis.core.StringRedisTemplate} is injected to
	 * explicitly set the TTL on each authorization record after saving, since
	 * {@link org.springframework.data.redis.core.TimeToLive} on an abstract entity class
	 * is not reliably applied by Spring Data Redis. The TTL is derived from the access
	 * token's expiry time plus a one-hour grace period.
	 *
	 * @param registeredClientRepository                the repository used to look up registered clients
	 *                                                  when reconstructing {@link org.springframework.security.oauth2.server.authorization.OAuth2Authorization} objects
	 * @param authorizationGrantAuthorizationRepository the Spring Data Redis repository backing
	 *                                                  the authorization grant entities
	 * @param stringRedisTemplate                       used to set explicit TTLs on authorization
	 *                                                  hash keys after saving
	 * @return a {@link RedisOAuth2AuthorizationService} backed by Redis
	 */
	@Bean
	public RedisOAuth2AuthorizationService authorizationService(RegisteredClientRepository registeredClientRepository,
	                                                            OAuth2AuthorizationGrantAuthorizationRepository authorizationGrantAuthorizationRepository,
	                                                            StringRedisTemplate stringRedisTemplate) {
		return new RedisOAuth2AuthorizationService(registeredClientRepository, authorizationGrantAuthorizationRepository, stringRedisTemplate);
	}

	/**
	 * Provides a Redis-backed {@link org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService} that stores
	 * user consent records in Redis.
	 * <p>
	 * Consent records capture which scopes a user has approved for a given client.
	 * Sharing them across instances via Redis ensures that a user who has already
	 * granted consent is not prompted again regardless of which token generator
	 * instance handles the subsequent authorization request.
	 *
	 * @param userConsentRepository the Spring Data Redis repository backing the consent entities
	 * @return a {@link RedisOAuth2AuthorizationConsentService} backed by Redis
	 */
	@Bean
	public RedisOAuth2AuthorizationConsentService authorizationConsentService(OAuth2UserConsentRepository userConsentRepository) {
		return new RedisOAuth2AuthorizationConsentService(userConsentRepository);
	}

	/**
	 * Re-registers the standard Redis health indicator, under Boot's default bean name
	 * and {@code /actuator/health} key ({@code redis}), for {@code nidam.session-mode=redis}
	 * deployments only.
	 *
	 * <p>Boot's {@code RedisHealthContributorAutoConfiguration} is excluded application-wide
	 * in {@link nidam.tokengenerator.TokenGeneratorApplication} because it activates purely off the presence of a
	 * {@link org.springframework.data.redis.connection.RedisConnectionFactory} bean, with no
	 * regard for {@code nidam.session-mode}. In {@code tomcat} mode the token generator has
	 * zero functional dependency on Redis, so without the exclusion a Redis outage still flips
	 * {@code /actuator/health} to {@code DOWN} for no operationally meaningful reason — this
	 * service is entirely internal, so nothing is even watching that status to make a routing
	 * decision off it. It's a false alarm, not a signal.</p>
	 *
	 * <p>This bean restores the indicator only when {@code session-mode=redis}, where Redis
	 * genuinely backs OAuth2 authorization state via {@link RedisOAuth2Config} and, when also
	 * configured, HTTP sessions via {@link RedisSessionConfig}. The bean name
	 * {@code redisHealthIndicator} deliberately matches the name Boot's own autoconfiguration
	 * would have used, so the resulting health JSON shape is identical to the default —
	 * nothing downstream needs to change to consume it.</p>
	 *
	 * @see RedisOAuth2Config
	 * @see RedisSessionConfig
	 * @see nidam.tokengenerator.TokenGeneratorApplication
	 */
	@Bean
	public HealthIndicator redisHealthIndicator(RedisConnectionFactory connectionFactory) {
		return new RedisHealthIndicator(connectionFactory);
	}

}
