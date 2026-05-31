package nidam.registration.services.mapper;

import nidam.registration.entities.mongo.User;
import nidam.registration.services.dto.UserRegistrationDto;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = UserRegistrationMapperConfig.class)
public interface UserRegistrationMongoMapper {

	@InheritConfiguration(name = "baseMapping")
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "authoritiesIds", ignore = true)
	User toEntity(UserRegistrationDto dto);

	UserRegistrationDto toDto(User user);
}
