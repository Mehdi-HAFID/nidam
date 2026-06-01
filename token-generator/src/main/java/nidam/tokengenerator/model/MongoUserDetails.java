package nidam.tokengenerator.model;

import nidam.tokengenerator.entities.mongo.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MongoDB-backed implementation of {@link UserDetails}.
 *
 * <p>Wraps a {@link User} document and a resolved list of authority names,
 * exposing them in a format compatible with Spring Security.</p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 * <li>Provides user credentials (email and password)</li>
 * <li>Maps authority names to {@link SimpleGrantedAuthority}</li>
 * <li>Delegates account status flags to the underlying document</li>
 * </ul>
 *
 * <p>Email is used as the username.</p>
 */

public class MongoUserDetails implements UserDetails {

//	private final User user;
//	private final List<String> authoritiesAsString;

	private final String email;
	private final Collection<? extends GrantedAuthority> authorities;
	private final String password;
	private final boolean enabled;


	public MongoUserDetails(String email, Collection<? extends GrantedAuthority> authorities, String password, boolean isEnabled) {
		this.email = email;
		this.authorities = authorities;
		this.password = password;
		this.enabled = isEnabled;
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
