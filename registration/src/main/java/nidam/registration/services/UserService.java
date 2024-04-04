package nidam.registration.services;

import nidam.registration.entities.Authority;
import nidam.registration.entities.Role;
import nidam.registration.entities.User;
import nidam.registration.repositories.AuthorityRepository;
import nidam.registration.repositories.RoleRepository;
import nidam.registration.repositories.UserRepository;
import nidam.registration.services.dto.UserRegisteredDto;
import nidam.registration.services.dto.UserRegistrationCaptchaDto;
import nidam.registration.services.dto.UserRegistrationDto;
import nidam.registration.services.error.AlreadyExistException;
import nidam.registration.services.error.PasswordInvalidException;
import nidam.registration.services.error.ReCaptchaException;
import nidam.registration.services.mapper.UserRegisteredMapper;
import nidam.registration.services.mapper.UserRegistrationCaptchaMapper;
import nidam.registration.services.mapper.UserRegistrationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static java.lang.String.format;

@Service
public class UserService {

	private Logger log = Logger.getLogger(UserService.class.getName());

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuthorityRepository authorityRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserRegistrationMapper registrationMapper;

	@Autowired
	private UserRegisteredMapper registeredMapper;

	@Value("#{${custom.authorities}}")
	private List<String> authorities;

	@Value("${custom.authType}")
	private String authType;

	@Value("#{${custom.roles}}")
	private List<String> roles;

	@Autowired
	private UserRegistrationCaptchaMapper userRegistrationCaptchaMapper;

	@Autowired
	private RecaptchaService recaptchaService;

	public UserRegisteredDto save(UserRegistrationCaptchaDto userDto){
		boolean result = recaptchaService.validateCaptcha(userDto.getRecaptchaKey());
		log.info("result: " + result);
		if(!result){
			throw new ReCaptchaException("Captcha Error");
		}
		return save(userRegistrationCaptchaMapper.toEntity(userDto));
	}

	public UserRegisteredDto save(UserRegistrationDto userDto){
		if(userRepository.findUserByEmail(userDto.getEmail()).isPresent()){
			throw new AlreadyExistException(format("Email %s already used!", userDto.getEmail()));
		}
		User user = registrationMapper.toEntity(userDto);

		setPassword(user);

		if( Integer.parseInt(authType) == 1){
			setAuthorities(user);
		} else if(Integer.parseInt(authType) == 2){
			setRoles(user);
		}

		user.setEnabled(true);

		user = userRepository.save(user);
		log.info("entity: " + user);

		UserRegisteredDto dto = registeredMapper.toDto(user);
		log.info("return dto: " + dto);
		return dto;
	}

	private void setPassword(User user) {
		if(!validatePassword(user.getPassword())){
			throw new PasswordInvalidException("Password violate constraints");
		}
		String encoded = passwordEncoder.encode(user.getPassword());
		user.setPassword(encoded);
	}

	private boolean validatePassword(String password){
		if(password.length() < 2){
			return false;
		}
		return true;
	}


	private void setAuthorities(User user) {
		// Admin User should always have all the authorities
		List<Authority> allAuthorities = new ArrayList<>();

		for(String authority : authorities){
			allAuthorities.add(authorityRepository.findAuthorityByName(authority));
		}

		user.getAuthorities().addAll(allAuthorities);
	}

	private void setRoles(User user) {
		// Admin User should always have all the authorities
		List<Role> allRoles = new ArrayList<>();

		for(String role : roles){
			allRoles.add(roleRepository.findRoleByName(role));
		}

		user.getRoles().addAll(allRoles);
	}

//	public User get(String email){
//		return userRepository.findUserByEmail(email).orElseThrow();
//	}
}
