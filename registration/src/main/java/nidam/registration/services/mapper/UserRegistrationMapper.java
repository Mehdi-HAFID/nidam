package nidam.registration.services.mapper;

import nidam.registration.entities.User;
import nidam.registration.services.dto.UserRegistrationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserRegistrationMapper {

	@Mapping(target = "email", source = "dto.email")
	@Mapping(target = "password", source = "dto.password")
	@Mapping(target = "roles", ignore = true)
	@Mapping(target = "authorities", ignore = true)
	@Mapping(target = "enabled", ignore = true)
	public User toEntity(UserRegistrationDto dto);

	public UserRegistrationDto toDto(User user);
}
