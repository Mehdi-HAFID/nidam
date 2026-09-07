package nidam.microprofile;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

	public record ErrorDto(int status, String code, String message) {
	}

	@Override
	public Response toResponse(ForbiddenException exception) {
		ErrorDto body = new ErrorDto(403, "ACCESS_DENIED", "You do not have sufficient permissions");
		return Response.status(Response.Status.FORBIDDEN)
				.entity(body)
				.type(MediaType.APPLICATION_JSON)
				.build();
	}
}