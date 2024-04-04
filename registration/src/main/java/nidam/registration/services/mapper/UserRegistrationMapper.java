package nidam.registration.services.mapper;

import nidam.registration.entities.User;
import nidam.registration.services.dto.UserRegistrationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserRegistrationMapper {

	@Mapping(target = "email", source = "dto.email")
	@Mapping(target = "password", source = "dto.password")
	public User toEntity(UserRegistrationDto dto);

	public UserRegistrationDto toDto(User user);
}
