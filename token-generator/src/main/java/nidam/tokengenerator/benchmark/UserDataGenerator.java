package nidam.tokengenerator.benchmark;

import nidam.tokengenerator.entities.Authority;
import nidam.tokengenerator.entities.User;
import nidam.tokengenerator.repositories.AuthorityRepository;
import nidam.tokengenerator.repositories.UserRepository;
import nidam.tokengenerator.service.RealLoginFailureAuditor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.logging.Logger;

@Component
public class UserDataGenerator {

	private final Logger log = Logger.getLogger(UserDataGenerator.class.getName());

//	@Bean
//	public CommandLineRunner initUsers(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthorityRepository authRepo) {
//		return args -> {
//			int totalUsers = 1000;
//
//			authRepo.save(new Authority("manage_users"));
//			authRepo.save(new Authority("manage-projects"));
//			List<Authority> authorities = authRepo.findAll();
////					Stream.of("manage_users", "manage-projects").map(auth -> new Authority(auth)).toList();
//
//			long start = System.currentTimeMillis();
//
//			for (int i = 1; i <= totalUsers; i++) {
//				String email = "user" + String.format("%04d", i) + "@nidam.com"; // user0001 .. user1000
//				String rawPassword = "pass" + i;                     // pass1 .. pass1000
//
//				User user = new User();
//				user.setEmail(email);
//				user.setPassword(passwordEncoder.encode(rawPassword));
//				user.setEnabled(true);
//				user.setAuthorities(authorities);
//				userRepository.save(user);
//			}
//
//			long end = System.currentTimeMillis();
//			long durationMs = end - start;
//			double usersPerSec = (totalUsers * 1000.0) / durationMs;
//
//			log.info("✅ Inserted " + totalUsers + " users.");
//			log.info("⏱️ Took " + durationMs + " ms (" + String.format("%.2f", usersPerSec) + " users/sec).");
//
//		};
//	}
}
