package nidam.micronaut.exception;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.security.authentication.AuthorizationException;
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

@Produces
@NullMarked
@Singleton
@Replaces(DefaultAuthorizationExceptionHandler.class)
public class NidamAccessDeniedHandler implements ExceptionHandler<AuthorizationException, HttpResponse<?>> {

	@Override
	public HttpResponse<?> handle(HttpRequest request, AuthorizationException exception) {
		if (exception.isForbidden()) {
			return HttpResponse.status(HttpStatus.FORBIDDEN)
					.body(Map.of("status", 403,
							"code", "ACCESS_DENIED",
							"message", "You do not have sufficient permissions"));
		}
		return HttpResponse.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("status", 401,
						"code", "UNAUTHORIZED",
						"message", "Authentication required"));
	}
}