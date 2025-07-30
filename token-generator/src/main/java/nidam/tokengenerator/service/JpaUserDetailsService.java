package nidam.tokengenerator.service;

import nidam.tokengenerator.entities.User;
import nidam.tokengenerator.model.EntityUserDetails;
import nidam.tokengenerator.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.logging.Logger;

@Service
public class JpaUserDetailsService implements UserDetailsService {

	private Logger log = Logger.getLogger(JpaUserDetailsService.class.getName());

	private final UserRepository userRepository;

	public JpaUserDetailsService(UserRepository userRepository){
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		Optional<User> userOptional = userRepository.findUserByEmail(email);
		if(userOptional.isPresent()){
			EntityUserDetails entityUserDetails = new EntityUserDetails(userOptional.get());
			log.info("entityUserDetails: " + entityUserDetails.getUsername() + " , authorities: " + entityUserDetails.getAuthorities());
			return entityUserDetails;
		}
		throw new UsernameNotFoundException("User not found");
	}

}
