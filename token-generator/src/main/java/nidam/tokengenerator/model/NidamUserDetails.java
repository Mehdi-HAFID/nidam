package nidam.tokengenerator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import nidam.tokengenerator.redis.convert.NidamUserDetailsMixin;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Nidam implementation of {@link UserDetails} and {@link CredentialsContainer}.
 *
 * <p>Represents a flattened security principal for an authenticated user. Designed
 * for safe serialization across distributed session storage (Spring Session with Redis)
 * and Redis-backed OAuth2 authorization records.</p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *     <li><b>Principal identification:</b> Uses the user's email address as the
 *     {@code username}.</li>
 *     <li><b>Authority management:</b> Holds a pre-resolved collection of
 *     {@link GrantedAuthority} objects mapped from the user's authorities.</li>
 *     <li><b>Credential erasure:</b> Implements {@link CredentialsContainer#eraseCredentials()}
 *     so that Spring Security automatically nulls the password after authentication
 *     succeeds, preventing the password hash from being written to Redis session
 *     storage or OAuth2 authorization records.</li>
 * </ul>
 *
 * <h3>Serialization</h3>
 * <p>Jackson annotations ({@link com.fasterxml.jackson.annotation.JsonCreator},
 * {@link com.fasterxml.jackson.annotation.JsonProperty}) allow this class to be
 * reconstructed from JSON without a no-argument constructor, which is required
 * by both Spring Session's {@link org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer}
 * and the custom Redis byte converters used for OAuth2 authorization persistence.</p>
 *
 * <p>A {@link NidamUserDetailsMixin} is registered
 * on the converter-level {@link com.fasterxml.jackson.databind.ObjectMapper} to allowlist
 * this class for polymorphic deserialization by Spring Security's Jackson infrastructure.
 * Without it, deserializing a {@code UsernamePasswordAuthenticationToken} whose principal
 * is a {@code NidamUserDetails} from Redis would fail with an
 * {@link IllegalArgumentException} indicating the class is not in the allowlist.</p>
 */
public class NidamUserDetails implements UserDetails, CredentialsContainer {

	private final String email;
	private final Collection<? extends GrantedAuthority> authorities;
	private String password;
	private final boolean enabled;

	private boolean accountNonExpired;
	private boolean accountNonLocked;
	private boolean credentialsNonExpired;

	/**
	 * Reconstructs the user details from serialized JSON or standard instantiation.
	 * <p>
	 * The {@link JsonCreator} and {@link JsonProperty} annotations are strictly required
	 * to allow Jackson to hydrate this immutable object dynamically without a default
	 * no-arguments constructor.
	 *
	 * @param email                 the user's email (serves as the username)
	 * @param authorities           the user's granted authorities (e.g., roles/permissions)
	 * @param password              the user's hashed password
	 * @param enabled               whether the account is currently active
	 * @param accountNonExpired     flag indicating if the account has expired
	 * @param accountNonLocked      flag indicating if the account is locked
	 * @param credentialsNonExpired flag indicating if the credentials have expired
	 */
	@JsonCreator
	public NidamUserDetails(@JsonProperty("username") String email, @JsonProperty("authorities")Collection<? extends GrantedAuthority> authorities,
	                        @JsonProperty("password") String password, @JsonProperty("enabled") boolean enabled,
	                        @JsonProperty("accountNonExpired") boolean accountNonExpired,
	                        @JsonProperty("accountNonLocked") boolean accountNonLocked,
	                        @JsonProperty("credentialsNonExpired") boolean credentialsNonExpired) {
		this.email = email;
		this.authorities = authorities;
		this.password = password;
		this.enabled = enabled;
		this.accountNonExpired = accountNonExpired;
		this.accountNonLocked = accountNonLocked;
		this.credentialsNonExpired = credentialsNonExpired;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void eraseCredentials() {
		this.password = null;
	}
}
