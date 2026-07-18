package nidam.bff.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

import java.time.Duration;

/**
 * Configures Redis-backed session serialization for the BFF, active only when
 * {@code nidam.session-mode=redis}.
 * <p>
 * Spring Session persists {@code SPRING_SECURITY_CONTEXT} and the OAuth2 authorized-client
 * attribute as opaque blobs in Redis. Both attributes hold polymorphic Spring Security types
 * (e.g. {@code OAuth2AuthenticationToken}, {@code DefaultOidcUser}, {@code OAuth2AuthorizedClient})
 * whose concrete runtime type Jackson can't infer from the declared field type alone — without
 * type metadata embedded in the JSON, deserialization on read-back fails or silently produces
 * the wrong type.
 * <p>
 * {@code @EnableRedisWebSession} explicitly enables the Redis-backed reactive
 * {@code WebSessionManager} here, rather than relying on Spring Boot's own session
 * autoconfiguration — which is excluded application-wide in {@code BffApplication} to prevent
 * it firing regardless of {@code nidam.session-mode} and colliding with the manual
 * {@code webSessionManager} bean used in {@code tomcat} mode. That also means the namespace
 * and timeout Boot used to apply automatically from {@code spring.session.*} properties are
 * no longer wired in automatically — {@link #springBootSessionRepositoryCustomizer()} below
 * reapplies them explicitly.
 */
@Configuration
@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "redis")
@EnableRedisWebSession
public class RedisSessionConfig {
	private static final String JAVA_UTIL_PACKAGE = "java.util";
	private static final String JAVA_TIME_PACKAGE = "java.time";
	private static final String JAVA_LANG_PACKAGE = "java.lang";
	private static final String ORG_SPRINGFRAMEWORK_SECURITY_PACKAGE = "org.springframework.security";
	private static final String JAVA_NET_PACKAGE = "java.net";

	@Value("${spring.session.redis.namespace:nidam:token-generator}")
	private String redisNamespace;

	@Value("${spring.session.timeout:7d}")
	private Duration sessionTimeout;

	/**
	 * Builds the {@link RedisSerializer} Spring Session uses to (de)serialize session attributes.
	 * <p>
	 * Default typing is required so Jackson embeds {@code @class} metadata per the reasoning
	 * above, but unrestricted default typing is a deserialization-gadget risk — so it's scoped
	 * to an explicit allow-list ({@link BasicPolymorphicTypeValidator}) covering only the
	 * packages actually present in our session attributes (JDK collections/time/lang, our OAuth2
	 * client's {@code java.net} URIs, and Spring Security types). Anything outside this list
	 * fails closed rather than deserializing arbitrary classes from Redis.
	 * <p>
	 * {@link SecurityJackson2Modules#getModules} registers the mixins Spring Security ships for
	 * its own types (including {@code OAuth2ClientJackson2Module} for
	 * {@code OAuth2AuthorizedClient}), which is what lets the polymorphic types above actually
	 * round-trip correctly rather than just being permitted by the type validator.
	 */
	@Bean
	public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
		ObjectMapper mapper = new ObjectMapper();

		BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
				.allowIfSubType(java.util.Collection.class)
				.allowIfSubType(JAVA_UTIL_PACKAGE)
				.allowIfSubType(JAVA_TIME_PACKAGE)
				.allowIfSubType(JAVA_LANG_PACKAGE)
				.allowIfSubType(JAVA_NET_PACKAGE)
				.allowIfSubType(ORG_SPRINGFRAMEWORK_SECURITY_PACKAGE)
				.allowIfSubType(java.util.Map.class)
				.build();

		mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

		mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));

		return new GenericJackson2JsonRedisSerializer(mapper);
	}

	/**
	 * Reapplies the namespace and timeout that Spring Boot's own session autoconfiguration
	 * used to set automatically before it was excluded in {@code BffApplication}.
	 * <p>
	 * {@code @EnableRedisWebSession}'s own {@code redisNamespace}/{@code maxInactiveIntervalInSeconds}
	 * attributes can't be used for this instead, since annotation attributes must be compile-time
	 * constants — they can't read {@code redisNamespace}/{@code sessionTimeout} from externalized
	 * configuration the way this customizer can via field injection. Without this bean, both values
	 * would silently fall back to Spring Session's hardcoded defaults (30-minute timeout,
	 * {@code spring:session:} namespace) rather than the values actually configured for this app.
	 */
	@Bean
	public ReactiveSessionRepositoryCustomizer<ReactiveRedisSessionRepository> springBootSessionRepositoryCustomizer() {
		return sessionRepository -> {
			sessionRepository.setRedisKeyNamespace(redisNamespace);
			sessionRepository.setDefaultMaxInactiveInterval(sessionTimeout);
		};
	}

}
