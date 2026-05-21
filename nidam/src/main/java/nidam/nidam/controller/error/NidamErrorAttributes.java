package nidam.nidam.controller.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Custom error attribute provider that standardizes API error responses.
 *
 * <p>Overrides Spring Boot's {@link DefaultErrorAttributes} to:
 * <ul>
 *   <li>Replace default error structure with a consistent API format</li>
 *   <li>Map HTTP status codes to application-specific error codes</li>
 *   <li>Log structured error events</li>
 * </ul>
 * </p>
 *
 * <p>This component is used by Spring Boot's global error handling mechanism
 * (e.g., {@code /error} endpoint).</p>
 *
 * <p><b>Note:</b>
 * <ul>
 *   <li>Does NOT handle 403 errors — those are handled by {@link NidamAccessDeniedHandler}</li>
 *   <li>401 errors are typically intercepted earlier by the BFF layer</li>
 * </ul>
 * </p>
 *
 * <p>Response format example:
 * <pre>
 * {
 *   "status": 404,
 *   "code": "NOT_FOUND",
 *   "message": "Resource not found"
 * }
 * </pre>
 * </p>
 */
@Component
public class NidamErrorAttributes extends DefaultErrorAttributes {
	private static final Logger log = Logger.getLogger(NidamErrorAttributes.class.getName());

	/**
	 * Builds custom error attributes based on HTTP status.
	 *
	 * <p>Maps standard HTTP status codes to domain-specific error responses:
	 * <ul>
	 *   <li>400 → VALIDATION_ERROR</li>
	 *   <li>404 → NOT_FOUND</li>
	 *   <li>405 → METHOD_NOT_ALLOWED</li>
	 *   <li>401 → UNAUTHORIZED (should not normally occur in this architecture)</li>
	 *   <li>Others → INTERNAL_ERROR</li>
	 * </ul>
	 * </p>
	 *
	 * @param webRequest the current request
	 * @param options    error attribute options
	 * @return a customized error response body
	 */
	@Override
	public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
//		log.info("NidamErrorAttributes entered");
		Map<String, Object> attrs = super.getErrorAttributes(webRequest, options);

		int status = (int) attrs.getOrDefault("status", 500);
		// Extract request info
		HttpServletRequest request = (HttpServletRequest) ((ServletWebRequest) webRequest).getNativeRequest();
		String path    = (String)  attrs.getOrDefault("path", request.getRequestURI());
		String method  = request.getMethod();
		String userEmail = request.getUserPrincipal().getName();

		return switch (status) {
			case 400 -> {
				log.info(String.format("400 VALIDATION_ERROR | method: %s | path: %s | caller: %s", method, path, userEmail));
				yield Map.of("status", 400, "code", "VALIDATION_ERROR", "message", "Invalid request data");
			}
			case 404 -> {
				log.info(String.format("404 NOT_FOUND | method: %s | path: %s | caller: %s", method, path, userEmail));
				yield Map.of("status", 404, "code", "NOT_FOUND", "message", "Resource not found");
			}
			case 405 -> {
				log.info(String.format("405 METHOD_NOT_ALLOWED | method: %s | path: %s | caller: %s", method, path, userEmail));
				yield Map.of("status", 405, "code", "METHOD_NOT_ALLOWED", "message", "HTTP method not allowed");
			}
			case 401 -> {
				// this should not be reachable because of the bff
				log.info(String.format("401 UNAUTHORIZED | method: %s | path: %s | caller: %s", method, path, userEmail));
				yield Map.of("status", 401, "code", "UNAUTHORIZED", "message", "Authentication required");
			}
			// this does NOT override NidamAccessDeniedHandler.
//			case 403 -> {
//				log.info(String.format("403 ACCESS_DENIED | method: %s | path: %s | caller: %s", method, path, getUserEmail()));
//				yield Map.of("status", 403, "code", "ACCESS_DENIED", "message", "You do overrrrrride permissions");
//			}
			default -> {
				log.info(String.format("500 INTERNAL_ERROR | method: %s | path: %s | caller: %s", method, path, userEmail));
				yield Map.of("status", status, "code", "INTERNAL_ERROR", "message", "Unexpected error");
			}
		};
	}

}
