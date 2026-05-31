package nidam.registration.services.mapper;

import nidam.registration.entities.mongo.Authority;
import nidam.registration.entities.mongo.User;
import nidam.registration.services.dto.UserRegisteredDto;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class UserRegisteredMongoMapper {

//	public User toEntity(UserRegistrationDto dto);

	@Mapping(target = "authorities", expression = "java(authoritiesIds)")
	@Mapping(target = "roles", ignore = true)
	public abstract UserRegisteredDto toDto(User user, List<String> authoritiesIds);

	public String authorityToString(Authority authority){
		return authority.getName();
	}

	public String IdToString(ObjectId id){
		return id.toString();
	}

}
