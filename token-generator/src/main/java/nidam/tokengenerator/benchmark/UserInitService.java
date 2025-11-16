package nidam.tokengenerator.benchmark;

import nidam.tokengenerator.entities.Authority;
import nidam.tokengenerator.entities.User;
import nidam.tokengenerator.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserInitService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserInitService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void insertUsersRange(int start, int end, List<Authority> authorities) {
		List<User> batch = new ArrayList<>(end - start + 1);

		for (int i = start; i <= end; i++) {
			String email = "user" + String.format("%04d", i) + "@nidam.com";		// user0001 .. user1000
			String rawPassword = "pass" + i;										// pass1 .. pass1000

			User user = new User();
			user.setEmail(email);
			user.setPassword(passwordEncoder.encode(rawPassword));
			user.setEnabled(true);
			user.setAuthorities(authorities);

			batch.add(user);
		}
		userRepository.saveAll(batch);
		userRepository.flush(); // optional, ensures commit happens immediately

	}
}
