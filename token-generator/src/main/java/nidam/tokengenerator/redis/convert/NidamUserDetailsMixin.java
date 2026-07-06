package nidam.tokengenerator.redis.convert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson mixin that allowlists {@link nidam.tokengenerator.model.NidamUserDetails}
 * for polymorphic deserialization by Spring Security's Jackson infrastructure.
 *
 * <p>Spring Security's {@code AllowlistTypeIdResolver} rejects deserialization of any
 * class not explicitly registered via a Spring Security Jackson module or mixin,
 * to prevent arbitrary class instantiation from untrusted JSON. Since
 * {@code NidamUserDetails} is a custom application type, it would otherwise fail
 * with an {@link IllegalArgumentException} ("not in the allowlist") whenever it
 * appears as the {@code principal} inside a serialized
 * {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken}.</p>
 *
 * <p>No visibility overrides are needed here because {@code NidamUserDetails} already
 * declares a {@link com.fasterxml.jackson.annotation.JsonCreator}-annotated constructor
 * with {@link com.fasterxml.jackson.annotation.JsonProperty} mappings for each field,
 * giving Jackson an explicit construction path without requiring field reflection.</p>
 *
 * <p>{@link JsonTypeInfo.Id#CLASS} embeds the {@code @class} property into the
 * serialized JSON so Jackson knows to instantiate {@code NidamUserDetails} specifically
 * rather than a generic {@code UserDetails} implementation when deserializing the
 * {@code principal} field of the stored
 * {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken}.</p>
 *
 * <p>Registered on the converter-level {@link com.fasterxml.jackson.databind.ObjectMapper}
 * in {@link nidam.tokengenerator.redis.config.RedisOAuth2Config#redisConverterObjectMapper()}.
 * Password exclusion from Redis is handled separately by
 * {@link nidam.tokengenerator.model.NidamUserDetails} implementing
 * {@link org.springframework.security.core.CredentialsContainer}, which Spring Security
 * calls automatically after authentication to null the password before it ever reaches
 * serialization.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class NidamUserDetailsMixin {

}
