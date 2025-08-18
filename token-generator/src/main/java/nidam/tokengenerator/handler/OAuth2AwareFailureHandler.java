package nidam.tokengenerator.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nidam.tokengenerator.service.LoginFailureAuditor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Custom authentication failure handler that maps exceptions to user-friendly messages.
 * <p>
 * Supports both standard Spring Security exceptions and OAuth2-specific errors.
 * Delegates failure tracking to a {@link LoginFailureAuditor}, and enforces temporary lockouts
 * based on configured thresholds.
 * <p>
 * On failure, stores the resolved error message in the session and redirects the user back to the login page.
 */
@Component
public class OAuth2AwareFailureHandler implements AuthenticationFailureHandler {
	private final Logger log = Logger.getLogger(OAuth2AwareFailureHandler.class.getName());

	private static final Map<Class<? extends AuthenticationException>, String> EXCEPTION_MESSAGES = Map.of(
			BadCredentialsException.class, "Invalid username or password.",
			LockedException.class, "Your account is locked. Please contact support.",
			DisabledException.class, "Your account is disabled.",
			AccountExpiredException.class, "Your account has expired.",
			CredentialsExpiredException.class, "Your password has expired.",
//			UsernameNotFoundException.class, "User not found.",
			AuthenticationServiceException.class, "Authentication service error. Try again later."
	);

	private final LoginFailureAuditor rateLimiter;

	public OAuth2AwareFailureHandler(LoginFailureAuditor rateLimiter) {
		this.rateLimiter = rateLimiter;
	}

	/**
	 * Handles authentication failure events.
	 * <p>
	 * Resolves the error message based on the exception type, logs the failure,
	 * stores the message in the session, and redirects the user to the login page.
	 * If the user is currently locked out, a lockout message is shown instead.
	 *
	 * @param request the {@link HttpServletRequest} during authentication
	 * @param response the {@link HttpServletResponse} to send the redirect
	 * @param exception the {@link AuthenticationException} that occurred
	 * @throws IOException if an error occurs during redirect
	 */
	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {

		if (recordFailure(request, response)) {
			response.sendRedirect("/auth/login");
			return;
		}

		String message = resolveErrorMessage(exception);
		log.warning("Authentication failed: " + exception.getClass().getSimpleName() + " - "+ exception.getMessage());

		// Optionally store error in session or request
		request.getSession().setAttribute("LOGIN_ERROR", message);
		// Redirect back to login page with a query param
		response.sendRedirect("/auth/login");
	}

	/**
	 * Records a login failure attempt and checks for lockout status.
	 * <p>
	 * Constructs a unique key from the username and client IP address,
	 * delegates failure tracking to the {@code rateLimiter}, and determines
	 * whether the source is currently locked out due to excessive failures.
	 * <p>
	 * If locked out, logs the event, stores a lockout message in the session,
	 * and signals that the login attempt should be blocked.
	 *
	 * @param request the {@link HttpServletRequest} containing login parameters
	 * @param response the {@link HttpServletResponse} used for session updates
	 * @return {@code true} if the user is locked out and should be blocked, {@code false} otherwise
	 * @throws IOException if an error occurs while interacting with the response
	 */
	private boolean recordFailure(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String username = request.getParameter("username");
		String ip = request.getRemoteAddr();
		String key = username + "|" + ip;

		rateLimiter.recordFailure(key);

		if (rateLimiter.isLockedOut(key)) {
			log.warning("Login blocked for user: " + username + " from IP: " + ip + " due to repeated failures.");
			request.getSession().setAttribute("LOGIN_ERROR", "Too many failed attempts. Try again later.");
			return true;
		}
		return false;
	}

	/**
	 * Resolves a user-friendly error message based on the type of {@link AuthenticationException}.
	 * <p>
	 * Supports detailed messages for OAuth2 errors and common Spring Security exceptions.
	 *
	 * @param exception the {@link AuthenticationException} to interpret
	 * @return a localized error message suitable for display
	 */
	private String resolveErrorMessage(AuthenticationException exception) {
		if (exception instanceof OAuth2AuthenticationException authEx) {
			OAuth2Error error = authEx.getError();
			return switch (error.getErrorCode()) {
				case "invalid_grant" -> "Invalid credentials or expired authorization.";
				case "unauthorized_client" -> "Client is not authorized for this operation.";
				case "invalid_request" -> "Malformed authentication request.";
				case "access_denied" -> "Access denied. You may have rejected the login.";
				case "server_error" -> "Internal server error. Please try again.";
				case "temporarily_unavailable" -> "Authentication service is temporarily unavailable.";
				default -> "OAuth2 error: " + error.getErrorCode();
			};
		}

		return EXCEPTION_MESSAGES.getOrDefault(exception.getClass(), "Authentication failed. Please try again.");
	}
}
