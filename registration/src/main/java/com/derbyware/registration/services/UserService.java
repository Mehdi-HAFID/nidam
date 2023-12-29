package com.derbyware.registration.services;

import com.derbyware.registration.entities.Authority;
import com.derbyware.registration.entities.Role;
import com.derbyware.registration.entities.User;
import com.derbyware.registration.repositories.AuthorityRepository;
import com.derbyware.registration.repositories.RoleRepository;
import com.derbyware.registration.repositories.UserRepository;
import com.derbyware.registration.services.dto.UserRegisteredDto;
import com.derbyware.registration.services.dto.UserRegistrationDto;
import com.derbyware.registration.services.error.AlreadyExistException;
import com.derbyware.registration.services.error.PasswordInvalidException;
import com.derbyware.registration.services.mapper.UserRegisteredMapper;
import com.derbyware.registration.services.mapper.UserRegistrationMapper;
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
