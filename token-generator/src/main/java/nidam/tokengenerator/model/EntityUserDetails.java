package nidam.tokengenerator.model;

import nidam.tokengenerator.entities.sql.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * SQL-backed implementation of {@link UserDetails}.
 *
 * <p>Wraps a {@link User} entity and exposes its data in a format
 * compatible with Spring Security.</p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 * <li>Provides user credentials (email and password)</li>
 * <li>Maps relational authorities to {@link SimpleGrantedAuthority}</li>
 * <li>Delegates account status flags to the underlying entity</li>
 * </ul>
 *
 * <p>Email is used as the username.</p>
 */

public class EntityUserDetails implements UserDetails {

	private final User user;

	public EntityUserDetails(User user) {
		this.user = user;
	}

	public User getUser() {
		return user;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return user.getAuthorities().stream()
				.map(authority -> new SimpleGrantedAuthority(authority.getName()))
				.collect(Collectors.toList());
	}

	@Override
	public String getPassword() {
		return user.getPassword();
	}

	@Override
	public String getUsername() {
		return user.getEmail();
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
		return user.isEnabled();
	}
}
