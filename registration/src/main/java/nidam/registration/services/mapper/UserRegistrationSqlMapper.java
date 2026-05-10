package nidam.registration.services.mapper;

import nidam.registration.entities.sql.User;
import nidam.registration.services.dto.UserRegistrationDto;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;

@Mapper(config = UserRegistrationMapperConfig.class)
public interface UserRegistrationSqlMapper {

	@InheritConfiguration(name = "baseMapping")
	User toEntity(UserRegistrationDto dto);

	UserRegistrationDto toDto(User user);
}
