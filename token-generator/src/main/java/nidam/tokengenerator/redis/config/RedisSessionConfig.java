package nidam.tokengenerator.redis.config;

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
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import java.time.Duration;

/**
 * Configures Spring Session backed by Redis for HTTP session persistence.
 *
 * <p>Active only when {@code nidam.session-mode=redis}. When set to {@code tomcat}
 * (or omitted), this class is ignored entirely and the application uses Tomcat's
 * native in-memory {@link jakarta.servlet.http.HttpSession} with no Spring involvement.</p>
 *
 * <p>This class handles <b>only HTTP session state</b> — browser cookies, the
 * authenticated security context, and saved requests. It has no knowledge of OAuth2
 * authorization records (codes, tokens, consents), which are the responsibility of
 * {@link RedisOAuth2Config}. The two classes share the same Redis instance but write
 * to completely independent key namespaces and use independent serialization pipelines.</p>
 *
 * <p>This class configures a Spring Session-compatible {@link org.springframework.data.redis.serializer.RedisSerializer}
 * for HTTP session attributes (via the magic bean name {@code springSessionDefaultRedisSerializer})</p>
 *
 * <h3>Why {@code SessionAutoConfiguration} is excluded globally</h3>
 * <p>Spring Boot's {@code SessionAutoConfiguration} detects session store dependencies
 * on the classpath and automatically activates Spring Session. Since
 * {@code spring-session-data-redis} is always present regardless of the active mode,
 * it is excluded at the application level (see {@link nidam.tokengenerator.TokenGeneratorApplication})
 * to prevent Spring Session from activating when {@code session-mode=tomcat}.
 * As a result, standard {@code spring.session.*} properties are not automatically
 * bound, so this class manually injects and applies them via a
 * {@link org.springframework.session.config.SessionRepositoryCustomizer}.</p>
 *
 * @see RedisOAuth2Config
 * @see nidam.tokengenerator.TokenGeneratorApplication
 */
@Configuration
@ConditionalOnProperty(name = "nidam.session-mode", havingValue = "redis")
@EnableRedisHttpSession
public class RedisSessionConfig {

	@Value("${spring.session.redis.namespace:nidam:token-generator}")
	private String redisNamespace;

	@Value("${spring.session.timeout:12h}")
	private Duration sessionTimeout;

	private static final String JAVA_TIME_PACKAGE = "java.time";
	private static final String JAVA_LANG_PACKAGE = "java.lang";
	private static final String ORG_SPRINGFRAMEWORK_SECURITY_PACKAGE = "org.springframework.security";
	private static final String NIDAM_TOKENGENERATOR_PACKAGE = "nidam.tokengenerator";

	/**
	 * Customizes the {@link RedisSessionRepository} since default Spring Boot
	 * auto-configuration bindings are disabled.
	 *
	 * @return a {@link SessionRepositoryCustomizer} that applies the configured namespace
	 * and timeout duration to the Redis repository.
	 */
	@Bean
	public SessionRepositoryCustomizer<RedisSessionRepository> redisSessionCustomizer() {
		return repository -> {
			repository.setRedisKeyNamespace(redisNamespace);
			repository.setDefaultMaxInactiveInterval(sessionTimeout);
		};
	}

	//	TODO when switching to spring boot 4, use GenericJacksonJsonRedisSerializer instead of GenericJackson2JsonRedisSerializer,
	// 	because the latter is deprecated in favor of the former in spring boot 4.

	/**
	 * Configures the default Redis serializer for Spring Session to use JSON serialization
	 * instead of standard Java native serialization.
	 * <p>
	 * This custom configuration is specifically tailored to safely serialize and deserialize
	 * Spring Security and Spring Authorization Server contexts. It utilizes a custom
	 * {@link ObjectMapper} configured with the following constraints and capabilities:
	 * <ul>
	 * <li><b>Polymorphic Type Validation:</b> Restricts deserialization to specific, trusted
	 * types and packages (e.g., standard Java collections/time, Spring Security classes,
	 * and the custom {@code nidam.tokengenerator} domain). This safely prevents arbitrary
	 * code execution vulnerabilities during JSON deserialization.</li>
	 * <li><b>Type Information Inclusion:</b> Activates default typing using
	 * {@link JsonTypeInfo.As#PROPERTY}. This ensures type metadata is stored as a JSON
	 * property ({@code @class}), which is strictly required by Spring Security's Jackson
	 * mixins to accurately reconstruct nested security contexts and custom Principals.</li>
	 * <li><b>Module Registration:</b> Registers both standard Spring Security modules
	 * and the {@link OAuth2AuthorizationServerJackson2Module} to ensure proper handling
	 * of complex OAuth2 objects, such as saved OIDC requests and authorization consents.</li>
	 * </ul>
	 *
	 * @return a {@link GenericJackson2JsonRedisSerializer} equipped with a highly customized,
	 * security-aware {@link ObjectMapper}.
	 */
	@Bean
	public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
		ObjectMapper mapper = new ObjectMapper();

		BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
				.allowIfSubType(java.util.Collection.class)
				.allowIfSubType(JAVA_TIME_PACKAGE)
				.allowIfSubType(JAVA_LANG_PACKAGE)
				.allowIfSubType(ORG_SPRINGFRAMEWORK_SECURITY_PACKAGE)
				.allowIfSubType(NIDAM_TOKENGENERATOR_PACKAGE)
				.allowIfSubType(java.util.Map.class)
				.build();

		// MUST use JsonTypeInfo.As.PROPERTY for Spring Security compatibility
		mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

		// Register standard Security modules
		mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));

		// Register Authorization Server specific module
		mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
//		mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));

		return new GenericJackson2JsonRedisSerializer(mapper);
	}
}
