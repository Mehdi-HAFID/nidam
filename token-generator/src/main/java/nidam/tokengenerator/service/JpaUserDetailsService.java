package nidam.tokengenerator.service;

import nidam.tokengenerator.entities.sql.User;
import nidam.tokengenerator.model.NidamUserDetails;
import nidam.tokengenerator.repositories.sql.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

/**
 * SQL-backed implementation of Spring Security's {@link UserDetailsService}.
 * <p>
 * This service is responsible for loading user-specific data during the authentication process.
 * It retrieves {@link nidam.tokengenerator.entities.sql.User} entities from the SQL database, resolves their associated authorities, and
 * translates them into a {@link NidamUserDetails} object (which is designed for Spring Security
 * compatibility and safe JSON serialization).
 * <p>
 * <b>Activation:</b> This bean is conditionally loaded into the Spring application context.
 * It will only be instantiated if the application property {@code nidam.persistence-mode}
 * is set to {@code sql} (or when the property is missing, as SQL is the default)..
 *
 * @see nidam.tokengenerator.repositories.sql.UserRepository
 * @see NidamUserDetails
 */
@Service
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "sql", matchIfMissing = true)
public class JpaUserDetailsService implements UserDetailsService {

	private static final Logger log = Logger.getLogger(JpaUserDetailsService.class.getName());

	private final UserRepository userRepository;

	/**
	 * Constructs the {@code JpaUserDetailsService} with the required database repositories.
	 *
	 * @param userRepository the repository for retrieving {@link nidam.tokengenerator.entities.sql.User} entities
	 */
	public JpaUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Locates the user based on the provided email address (which acts as the username)
	 * and constructs their security profile.
	 * <p>
	 * The authentication profile is built through a two-step process:
	 * <ol>
	 * <li>Retrieves the {@link nidam.tokengenerator.entities.sql.User} entity matching the provided email.</li>
	 * <li>Maps the user's authorities to Spring Security's {@link SimpleGrantedAuthority} format.</li>
	 * </ol>
	 *
	 * @param email the email address identifying the user whose data is to be loaded
	 * @return a fully hydrated {@link NidamUserDetails} instance representing the user
	 * @throws UsernameNotFoundException if no user entity exists for the provided email
	 */
	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findUserByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));

		NidamUserDetails userDetails = new NidamUserDetails(user.getEmail(), user.getAuthorities().stream()
				.map(authority -> new SimpleGrantedAuthority(authority.getName())).toList(),
				user.getPassword(), user.isEnabled(), true, true, true);
		log.info("entityUserDetails: " + userDetails.getUsername() + " , authorities: " + userDetails.getAuthorities());
		return userDetails;
	}
}
