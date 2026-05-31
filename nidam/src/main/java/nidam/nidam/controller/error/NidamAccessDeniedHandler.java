package nidam.nidam.controller.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Custom {@link AccessDeniedHandler} for handling HTTP 403 (Forbidden) responses.
 *
 * <p>This handler enhances the default OAuth2 behavior by:
 * <ul>
 *   <li>Delegating to {@link BearerTokenAccessDeniedHandler} to ensure proper
 *       OAuth2-compliant headers are set (e.g., {@code WWW-Authenticate}).</li>
 *   <li>Adding a consistent JSON response body for client consumption.</li>
 *   <li>Logging structured security events for observability.</li>
 * </ul>
 * </p>
 *
 * <p>This handler is triggered when:
 * <ul>
 *   <li>The user is authenticated</li>
 *   <li>But lacks sufficient authorities to access a resource</li>
 * </ul>
 * </p>
 *
 * <p><b>Important:</b> This does NOT handle authentication failures (401),
 * only authorization failures (403).</p>
 */
@Component
public class NidamAccessDeniedHandler implements AccessDeniedHandler {
	private static final Logger log = Logger.getLogger(NidamAccessDeniedHandler.class.getName());

	private final BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();

	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Handles access denied (403) exceptions.
	 *
	 * <p>Execution flow:
	 * <ol>
	 *   <li>Delegate to Spring's OAuth2 handler to set standard headers</li>
	 *   <li>If response is not committed, write a custom JSON body</li>
	 *   <li>Log the access denial event</li>
	 * </ol>
	 * </p>
	 *
	 * @param request  the HTTP request
	 * @param response the HTTP response
	 * @param ex       the access denied exception
	 * @throws IOException if writing the response fails
	 */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {

		// Step 1: let Spring handle OAuth2 (sets header + status)
		delegate.handle(request, response, ex);
		// ⚠️ If response already committed, stop
		if (response.isCommitted()) {
			log.info("response already committed");
			return;
		}

		// Step 2: add your JSON body
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", HttpStatus.FORBIDDEN.value());
		body.put("code", "ACCESS_DENIED");
		body.put("message", "You do not have sufficient permissions");

		mapper.writeValue(response.getOutputStream(), body);

		String path =  Optional.of(request.getRequestURI()).orElse("path");
		String method  = request.getMethod();
		log.info(String.format("403 ACCESS_DENIED | method: %s | path: %s | caller: %s", method, path, request.getUserPrincipal().getName()));
	}
}
