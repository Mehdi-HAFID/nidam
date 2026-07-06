package nidam.tokengenerator.redis.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Spring Data Redis {@link WritingConverter} that serializes a
 * {@link UsernamePasswordAuthenticationToken} to {@code byte[]} using Jackson.
 *
 * <p>Part of the custom converter set registered via
 * {@link org.springframework.data.redis.core.convert.RedisCustomConversions} in
 * {@link nidam.tokengenerator.redis.config.RedisOAuth2Config}. Required because
 * {@link UsernamePasswordAuthenticationToken} is polymorphic — its {@code principal}
 * field is typed as {@code Object} — and cannot be automatically flattened by Spring
 * Data Redis's {@link org.springframework.data.redis.core.convert.MappingRedisConverter}
 * into dot-notation hash entries.</p>
 *
 *     <b>Injected constructor</b> — accepts a pre-configured {@link ObjectMapper}
 *     from {@link nidam.tokengenerator.redis.config.RedisOAuth2Config#redisConverterObjectMapper()},
 *     which additionally registers {@link nidam.tokengenerator.redis.convert.NidamUserDetailsMixin}
 *     to allowlist the custom principal type for deserialization.
 * </ul>
 *
 * @see BytesToUsernamePasswordAuthenticationTokenConverter
 * @see nidam.tokengenerator.redis.config.RedisOAuth2Config
 */
@WritingConverter
public class UsernamePasswordAuthenticationTokenToBytesConverter implements Converter<UsernamePasswordAuthenticationToken, byte[]> {

	private final Jackson2JsonRedisSerializer<UsernamePasswordAuthenticationToken> serializer;

	/**
	 * Constructs the converter with the provided {@link ObjectMapper}.
	 * Intended to be called by
	 * {@link nidam.tokengenerator.redis.config.RedisOAuth2Config#redisCustomConversions}
	 * with the fully configured mapper that includes
	 * {@link nidam.tokengenerator.redis.convert.NidamUserDetailsMixin}.
	 *
	 * @param objectMapper the pre-configured mapper to use for serialization
	 */
	public UsernamePasswordAuthenticationTokenToBytesConverter(ObjectMapper objectMapper) {
		this.serializer = new Jackson2JsonRedisSerializer<>(objectMapper, UsernamePasswordAuthenticationToken.class);
	}

	@Override
	public byte[] convert(UsernamePasswordAuthenticationToken value) {
		return this.serializer.serialize(value);
	}

}
