package nidam.tokengenerator.service;

import nidam.tokengenerator.entities.mongo.User;
import nidam.tokengenerator.model.MongoUserDetails;
import nidam.tokengenerator.repositories.mongo.AuthorityRepository;
import nidam.tokengenerator.repositories.mongo.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

/**
 *
 * {@link UserDetailsService} implementation backed by MongoDB.
 *
 * <p>This service is activated when {@code nidam.persistence-mode=mongo}.</p>
 *
 * <p>It retrieves a {@link User} by email and resolves authorities
 * via {@link AuthorityRepository}, since MongoDB does not support joins.</p>
 * The result is adapted to {@link UserDetails} using {@link MongoUserDetails}.</p>
 *
 * <h3>Behavior</h3>
 * <ul>
 * <li>Loads user by email (used as username)</li>
 * <li>Fetches authorities using {@code authoritiesIds}</li>
 * <li>Throws {@link UsernameNotFoundException} if user does not exist</li>
 * </ul>
 */

@Service
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "mongo")
public class MongoUserDetailsService implements UserDetailsService {

	private static final Logger log = Logger.getLogger(MongoUserDetailsService.class.getName());

	private final UserRepository userRepository;
	private final AuthorityRepository authorityRepository;

	public MongoUserDetailsService(UserRepository userRepository, AuthorityRepository authorityRepository) {
		this.userRepository = userRepository;
		this.authorityRepository = authorityRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));

		List<SimpleGrantedAuthority> authorities = authorityRepository.findByIdIn(user.getAuthoritiesIds())
				.stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).toList();
		MongoUserDetails userDetails = new MongoUserDetails(user.getEmail(), authorities, user.getPassword(), user.isEnabled());
		log.info("mongoUserDetails: " + userDetails.getUsername() + " , authorities: " + userDetails.getAuthorities());
		return userDetails;
	}
}
