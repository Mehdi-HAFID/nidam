package nidam.registration.services.mapper;

import nidam.registration.entities.sql.Authority;
import nidam.registration.entities.sql.Role;
import nidam.registration.entities.sql.User;
import nidam.registration.services.dto.UserRegisteredDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class UserRegisteredSqlMapper {

//	public User toEntity(UserRegistrationDto dto);

	public abstract UserRegisteredDto toDto(User user);

	public String authorityToString(Authority authority){
		return authority.getName();
	}

	public String roleToString(Role role){
		return role.getName();
	}
}
