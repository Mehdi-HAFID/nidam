package com.derbyware.registration.controllers;

import com.derbyware.registration.entities.User;
import com.derbyware.registration.services.UserService;
import com.derbyware.registration.services.dto.UserRegisteredDto;
import com.derbyware.registration.services.dto.UserRegistrationDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.logging.Logger;

@RestController
public class UserRegistration {

	private Logger log = Logger.getLogger(UserRegistration.class.getName());

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private UserService userService;

	@PostMapping("/new")
	public ResponseEntity<UserRegisteredDto> register(@RequestBody UserRegistrationDto userDto){
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

	@Value("#{${custom.authorities}}")
	private List<String> authorities;

	@GetMapping("/authorities")
	public void validate(){
		log.info("authorities: " + authorities);
	}
}
