package nidam.registration.services.mapper;

import nidam.registration.services.dto.UserRegistrationDto;
import org.mapstruct.MapperConfig;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@MapperConfig(componentModel = "spring")
public interface UserRegistrationMapperConfig {

	@Mapping(target = "email", source = "dto.email")
	@Mapping(target = "password", source = "dto.password")
	@Mapping(target = "roles", ignore = true)
	@Mapping(target = "authorities", ignore = true)
	@Mapping(target = "enabled", ignore = true)
	void baseMapping(UserRegistrationDto dto, @MappingTarget Object target);
}
