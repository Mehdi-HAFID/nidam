package nidam.tokengenerator.service;

import nidam.tokengenerator.entities.mongo.User;
import nidam.tokengenerator.model.NidamUserDetails;
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
 * MongoDB-backed implementation of Spring Security's {@link UserDetailsService}.
 * <p>
 * This service is responsible for loading user-specific data during the authentication process.
 * It retrieves {@link User} documents from MongoDB, resolves their associated authorities, and
 * translates them into a {@link NidamUserDetails} object (which is designed for Spring Security
 * compatibility and safe JSON serialization).
 * <p>
 * <b>Activation:</b> This bean is conditionally loaded into the Spring application context.
 * It will only be instantiated if the application property {@code nidam.persistence-mode}
 * is explicitly set to {@code mongo}.
 *
 * @see UserRepository
 * @see AuthorityRepository
 * @see NidamUserDetails
 */
@Service
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "mongo")
public class MongoUserDetailsService implements UserDetailsService {

	private static final Logger log = Logger.getLogger(MongoUserDetailsService.class.getName());

	private final UserRepository userRepository;
	private final AuthorityRepository authorityRepository;

	/**
	 * Constructs the {@code MongoUserDetailsService} with the required database repositories.
	 *
	 * @param userRepository      the repository for retrieving {@link User} documents
	 * @param authorityRepository the repository for resolving linked authority names
	 */
	public MongoUserDetailsService(UserRepository userRepository, AuthorityRepository authorityRepository) {
		this.userRepository = userRepository;
		this.authorityRepository = authorityRepository;
	}

	/**
	 * Locates the user based on the provided email address (which acts as the username)
	 * and constructs their security profile.
	 * <p>
	 * The authentication profile is built through a two-step process:
	 * <ol>
	 * <li>Retrieves the {@link User} document matching the provided email.</li>
	 * <li>Queries the {@link AuthorityRepository} using the referenced authority IDs stored
	 * on the user document, mapping the results into {@link SimpleGrantedAuthority} objects.</li>
	 * </ol>
	 *
	 * @param email the email address identifying the user whose data is to be loaded
	 * @return a fully hydrated {@link NidamUserDetails} instance representing the user
	 * @throws UsernameNotFoundException if no user document exists for the provided email
	 */
	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));

		List<SimpleGrantedAuthority> authorities = authorityRepository.findByIdIn(user.getAuthoritiesIds())
				.stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).toList();
		NidamUserDetails userDetails = new NidamUserDetails(user.getEmail(), authorities, user.getPassword(), user.isEnabled(),
				true, true, true);
		log.info("mongoUserDetails: " + userDetails.getUsername() + " , authorities: " + userDetails.getAuthorities());
		return userDetails;
	}
}
