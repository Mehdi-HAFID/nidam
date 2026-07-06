package nidam.tokengenerator.redis.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Spring Data Redis {@link WritingConverter} that serializes an
 * {@link OAuth2AuthorizationRequest} to {@code byte[]} using Jackson.
 *
 * <p>Required because {@link OAuth2AuthorizationRequest} is a direct field on
 * {@link nidam.tokengenerator.redis.entity.OAuth2AuthorizationCodeGrantAuthorization}
 * and contains complex nested types — scopes, additional parameters, grant type,
 * response type — that {@link org.springframework.data.redis.core.convert.MappingRedisConverter}
 * cannot automatically flatten into dot-notation hash entries. The serialized blob
 * is stored as a single hash field value ({@code authorizationRequest}) within the
 * Redis hash.</p>
 *
 * <p>Accepts a pre-configured {@link ObjectMapper} injected from
 * {@link nidam.tokengenerator.redis.config.RedisOAuth2Config#redisConverterObjectMapper()}.</p>
 *
 * @see BytesToOAuth2AuthorizationRequestConverter
 * @see nidam.tokengenerator.redis.config.RedisOAuth2Config#redisCustomConversions
 */
@WritingConverter
public class OAuth2AuthorizationRequestToBytesConverter implements Converter<OAuth2AuthorizationRequest, byte[]> {

	private final Jackson2JsonRedisSerializer<OAuth2AuthorizationRequest> serializer;

	public OAuth2AuthorizationRequestToBytesConverter(ObjectMapper objectMapper) {
		this.serializer = new Jackson2JsonRedisSerializer<>(objectMapper, OAuth2AuthorizationRequest.class);
	}

	@Override
	public byte[] convert(OAuth2AuthorizationRequest value) {
		return this.serializer.serialize(value);
	}

}
