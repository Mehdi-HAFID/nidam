package nidam.tokengenerator.service;

import nidam.tokengenerator.entities.sql.User;
import nidam.tokengenerator.model.EntityUserDetails;
import nidam.tokengenerator.repositories.sql.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

/**

 * {@link UserDetailsService} implementation backed by a SQL database.
 *
 * <p>This service is activated when {@code nidam.persistence-mode=sql}
 * (or when the property is missing, as SQL is the default).</p>
 *
 * <p>It retrieves a {@link User} by email and adapts it to Spring Security's
 * {@link UserDetails} via {@link EntityUserDetails}.</p>
 *
 * <h3>Behavior</h3>
 * <ul>
 * <li>Loads user by email (used as username)</li>
 * <li>Throws {@link UsernameNotFoundException} if user does not exist</li>
 * <li>Maps user authorities directly from the relational model</li>
 * </ul>

 */

@Service
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "sql", matchIfMissing = true)
public class JpaUserDetailsService implements UserDetailsService {

	private static final Logger log = Logger.getLogger(JpaUserDetailsService.class.getName());

	private final UserRepository userRepository;

	public JpaUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findUserByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));

		EntityUserDetails userDetails = new EntityUserDetails(user);
		log.info("entityUserDetails: " + userDetails.getUsername() + " , authorities: " + userDetails.getAuthorities());
		return userDetails;
	}
}
