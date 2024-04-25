package nidam.registration.controllers;

import nidam.registration.services.UserService;
import nidam.registration.services.dto.UserRegisteredDto;
import nidam.registration.services.dto.UserRegistrationCaptchaDto;
import nidam.registration.services.dto.UserRegistrationDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
public class UserRegistration {

	private Logger log = Logger.getLogger(UserRegistration.class.getName());

	private UserService userService;

	public UserRegistration(UserService userService){
		this.userService = userService;
	}

	@PostMapping("/register")
	public ResponseEntity<UserRegisteredDto> register(@RequestBody UserRegistrationDto userDto){
		log.info("userDto: " + userDto);
		UserRegisteredDto entity = userService.save(userDto);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(entity);
	}

	@PostMapping("/registerCaptcha")
	public ResponseEntity<UserRegisteredDto> register(@RequestBody UserRegistrationCaptchaDto userDto){
		log.info("userDto: " + userDto);

		UserRegisteredDto entity = userService.save(userDto);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(entity);
	}

//	@GetMapping("/validate")
//	public void validate(){
//		User us2 = userService.get("mehdi2@mail.com");
//		User us3 = userService.get("mehdi3@mail.com");
//
//		boolean match2 = passwordEncoder.matches("MYpassword", us2.getPassword());
//		boolean match3 = passwordEncoder.matches("MYpassword", us3.getPassword());
//
//		log.info("Matching 2: " + match2 + " 3: " + match3);
//	}

//	@Value("#{${custom.authorities}}")
//	private List<String> authorities;
//
//	@GetMapping("/authorities")
//	public void validate(){
//		log.info("authorities: " + authorities);
//	}
}
