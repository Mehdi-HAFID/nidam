package nidam.tokengenerator.redis.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import nidam.tokengenerator.redis.entity.OAuth2AuthorizationGrantAuthorization;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

/**
 * Spring Data Redis {@link WritingConverter} that serializes a
 * {@link OAuth2AuthorizationGrantAuthorization.ClaimsHolder} to {@code byte[]} using Jackson.
 *
 * <p>Required because {@link OAuth2AuthorizationGrantAuthorization.ClaimsHolder} is a
 * direct field on {@link OAuth2AuthorizationGrantAuthorization} and wraps a
 * {@code Map<String, Object>} of JWT claims where values are typed as {@code Object},
 * holding types such as {@link java.time.Instant}, {@link java.net.URL},
 * {@link java.util.List}, and primitives. This heterogeneity makes automatic flattening
 * by {@link org.springframework.data.redis.core.convert.MappingRedisConverter} impossible
 * — the map must be serialized as a single JSON blob with type metadata preserved.</p>
 *
 * <p>Accepts a pre-configured {@link ObjectMapper} injected from
 * {@link nidam.tokengenerator.redis.config.RedisOAuth2Config#redisConverterObjectMapper()}.</p>
 *
 * @see BytesToClaimsHolderConverter
 * @see nidam.tokengenerator.redis.config.RedisOAuth2Config#redisCustomConversions
 */
@WritingConverter
public class ClaimsHolderToBytesConverter implements Converter<OAuth2AuthorizationGrantAuthorization.ClaimsHolder, byte[]> {

	private final Jackson2JsonRedisSerializer<OAuth2AuthorizationGrantAuthorization.ClaimsHolder> serializer;

	public ClaimsHolderToBytesConverter(ObjectMapper objectMapper) {
		this.serializer = new Jackson2JsonRedisSerializer<>(objectMapper, OAuth2AuthorizationGrantAuthorization.ClaimsHolder.class);
	}

	@Override
	public byte[] convert(OAuth2AuthorizationGrantAuthorization.ClaimsHolder value) {
		return this.serializer.serialize(value);
	}

}