package nidam.tokengenerator.redis.convert;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Data Redis {@link ReadingConverter} that deserializes a {@code byte[]} into a
 * {@link UsernamePasswordAuthenticationToken} using Jackson.
 *
 * <p>The symmetric counterpart to
 * {@link UsernamePasswordAuthenticationTokenToBytesConverter}. Reconstructs the token
 * from the JSON blob stored in the Redis hash field {@code principal}, including the
 * nested {@link nidam.tokengenerator.model.NidamUserDetails} principal when the
 * injected constructor is used.</p>
 *
 * @see UsernamePasswordAuthenticationTokenToBytesConverter
 * @see nidam.tokengenerator.redis.config.RedisOAuth2Config
 */
@ReadingConverter
public class BytesToUsernamePasswordAuthenticationTokenConverter implements Converter<byte[], UsernamePasswordAuthenticationToken> {

	private final JacksonJsonRedisSerializer<UsernamePasswordAuthenticationToken> serializer;

	/**
	 * Constructs the converter with the provided {@link ObjectMapper}.
	 * Intended to be called by
	 * {@link nidam.tokengenerator.redis.config.RedisOAuth2Config#redisCustomConversions}
	 * with the fully configured mapper that includes
	 * {@link nidam.tokengenerator.redis.convert.NidamUserDetailsMixin}, enabling deserialization
	 * of the {@link nidam.tokengenerator.model.NidamUserDetails} principal without
	 * triggering Spring Security's allowlist rejection.
	 *
	 * @param objectMapper the pre-configured mapper to use for deserialization
	 */
	public BytesToUsernamePasswordAuthenticationTokenConverter(ObjectMapper objectMapper) {
		this.serializer = new JacksonJsonRedisSerializer<>(objectMapper, UsernamePasswordAuthenticationToken.class);
	}

	@Override
	public UsernamePasswordAuthenticationToken convert(byte[] value) {
		return this.serializer.deserialize(value);
	}

}
