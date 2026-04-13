package nidam.registration.services.mapper;

import nidam.registration.entities.Authority;
import nidam.registration.entities.Role;
import nidam.registration.entities.User;
import nidam.registration.services.dto.UserRegisteredDto;
import org.mapstruct.Mapper;

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
