package com.derbyware.registration.services.mapper;

import com.derbyware.registration.entities.Authority;
import com.derbyware.registration.entities.Role;
import com.derbyware.registration.entities.User;
import com.derbyware.registration.services.dto.UserRegisteredDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class UserRegisteredMapper {

//	public User toEntity(UserRegistrationDto dto);

	public abstract UserRegisteredDto toDto(User user);

	public String authorityToString(Authority authority){
		return authority.getName();
	}

	public String roleToString(Role role){
		return role.getName();
	}
}
