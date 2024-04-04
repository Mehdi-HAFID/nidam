package nidam.registration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.logging.Logger;

@SpringBootApplication
public class RegistrationApplication {

	private Logger log = Logger.getLogger(RegistrationApplication.class.getName());

	public static void main(String[] args) {
		SpringApplication.run(RegistrationApplication.class, args);
	}

	@Value("#{${custom.authorities}}")
	private List<String> authorities;

	@Value("#{${custom.roles}}")
	private List<String> roles;

//	Run the first time
//	@Bean
//	ApplicationRunner configureRepository(AuthorityRepository authorityRepository) {
//		return args -> {
//			log.info("args: " + args);
//			for (String authority : authorities) {
//				Authority auth = new Authority(authority);
//				authorityRepository.save(auth);
//			}
//
//		};
//	}

	//	Run the first time
//	@Bean
//	ApplicationRunner configureRepository(RoleRepository roleRepository) {
//		return args -> {
//			for (String role : roles) {
//				Role roleEntity = new Role(role);
//				roleRepository.save(roleEntity);
//			}
//
//		};
//	}
}
