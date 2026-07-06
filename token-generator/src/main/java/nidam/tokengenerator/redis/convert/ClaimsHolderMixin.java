package nidam.tokengenerator.redis.convert;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson mixin that allowlists
 * {@link nidam.tokengenerator.redis.entity.OAuth2AuthorizationGrantAuthorization.ClaimsHolder}
 * for polymorphic deserialization and embeds {@code @class} type metadata into its
 * serialized form.
 *
 * <p>{@code ClaimsHolder} wraps a single {@code Map<String, Object>} of JWT claims
 * whose values are typed as {@code Object}, holding types such as
 * {@link java.time.Instant}, {@link java.net.URL}, and {@link java.util.List}.
 * The {@link JsonTypeInfo.Id#CLASS} annotation ensures type metadata is preserved
 * for those heterogeneous values during the round-trip through Redis.</p>
 *
 * <p>No constructor declaration is needed in this mixin because {@code ClaimsHolder}
 * already declares a {@link com.fasterxml.jackson.annotation.JsonCreator}-annotated
 * constructor with a {@link com.fasterxml.jackson.annotation.JsonProperty}-mapped
 * {@code claims} parameter, giving Jackson an explicit construction path.</p>
 *
 * <p>Used by {@link nidam.tokengenerator.redis.convert.ClaimsHolderToBytesConverter}
 * and {@link nidam.tokengenerator.redis.convert.BytesToClaimsHolderConverter}.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class ClaimsHolderMixin {

}
