package nidam.registration.services.mapper;

import nidam.registration.services.dto.UserRegistrationCaptchaDto;
import nidam.registration.services.dto.UserRegistrationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserRegistrationCaptchaMapper {

	@Mapping(target = "email", source = "dto.email")
	@Mapping(target = "password", source = "dto.password")
	public UserRegistrationDto toEntity(UserRegistrationCaptchaDto dto);

	public UserRegistrationCaptchaDto toDto(UserRegistrationDto user);
}
