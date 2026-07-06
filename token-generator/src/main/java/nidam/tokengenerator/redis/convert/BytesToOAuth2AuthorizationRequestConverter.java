package nidam.tokengenerator.redis.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Spring Data Redis {@link ReadingConverter} that deserializes a {@code byte[]} into an
 * {@link OAuth2AuthorizationRequest} using Jackson.
 *
 * <p>The symmetric counterpart to {@link OAuth2AuthorizationRequestToBytesConverter}.
 * Reconstructs the full authorization request — including URI, scopes, state, nonce,
 * and additional parameters — from the JSON blob stored in the Redis hash field
 * {@code authorizationRequest}.</p>
 *
 * @see OAuth2AuthorizationRequestToBytesConverter
 */
@ReadingConverter
public class BytesToOAuth2AuthorizationRequestConverter implements Converter<byte[], OAuth2AuthorizationRequest> {

	private final Jackson2JsonRedisSerializer<OAuth2AuthorizationRequest> serializer;

	public BytesToOAuth2AuthorizationRequestConverter(ObjectMapper objectMapper) {
		this.serializer = new Jackson2JsonRedisSerializer<>(objectMapper, OAuth2AuthorizationRequest.class);
	}

	@Override
	public OAuth2AuthorizationRequest convert(byte[] value) {
		return this.serializer.deserialize(value);
	}

}
