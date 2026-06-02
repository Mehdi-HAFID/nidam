package nidam.tokengenerator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Nidam implementation of {@link UserDetails}.
 *
 * <p>This class represents a flattened security profile for a user. It is explicitly
 * designed with Jackson annotations to guarantee seamless JSON serialization and
 * deserialization, making it perfectly suited for distributed session storage
 * (e.g., Spring Session with Redis).</p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 * <li><b>Principal Identification:</b> Uses the user's email address as the core {@code username}.</li>
 * <li><b>Authority Management:</b> Holds a pre-resolved collection of {@link GrantedAuthority} objects.</li>
 * <li><b>Account Status:</b> Tracks the user's {@code enabled} state. Note that while other
 * status flags (locked, expired) are accepted during instantiation, their getter methods
 * currently default to returning {@code true}.</li>
 * </ul>
 */
public class NidamUserDetails implements UserDetails {

	private final String email;
	private final Collection<? extends GrantedAuthority> authorities;
	private final String password;
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
}
