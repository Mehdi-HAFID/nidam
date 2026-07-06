package nidam.tokengenerator.redis.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import nidam.tokengenerator.redis.entity.OAuth2AuthorizationGrantAuthorization;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

/**
 * Spring Data Redis {@link ReadingConverter} that deserializes a {@code byte[]} into an
 * {@link OAuth2AuthorizationGrantAuthorization.ClaimsHolder} using Jackson.
 *
 * <p>The symmetric counterpart to {@link ClaimsHolderToBytesConverter}. Reconstructs
 * the JWT claims map from the JSON blob stored in the Redis hash fields
 * {@code accessToken.claims} and {@code idToken.claims}, restoring typed values
 * such as {@link java.time.Instant} and {@link java.net.URL} correctly via
 * the registered Jackson modules and {@link ClaimsHolderMixin}.</p>
 *
 * @see ClaimsHolderToBytesConverter
 */

@ReadingConverter
public class BytesToClaimsHolderConverter implements Converter<byte[], OAuth2AuthorizationGrantAuthorization.ClaimsHolder> {

	private final Jackson2JsonRedisSerializer<OAuth2AuthorizationGrantAuthorization.ClaimsHolder> serializer;

	public BytesToClaimsHolderConverter(ObjectMapper objectMapper) {
		this.serializer = new Jackson2JsonRedisSerializer<>(objectMapper, OAuth2AuthorizationGrantAuthorization.ClaimsHolder.class);
	}

	@Override
	public OAuth2AuthorizationGrantAuthorization.ClaimsHolder convert(byte[] value) {
		return this.serializer.deserialize(value);
	}

}