package nidam.tokengenerator.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nidam.tokengenerator.service.LoginFailureAuditor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom authentication success handler that resets login failure tracking upon successful authentication.
 * <p>
 * Extends {@link SavedRequestAwareAuthenticationSuccessHandler} to preserve the original redirect behavior
 * after login, while integrating with a {@link LoginFailureAuditor} to clear any recorded failures
 * for the current user and IP address.
 * <p>
 * This ensures that temporary lockout counters are reset once valid credentials are provided.
 */
@Component
public class OAuth2AwareSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private final LoginFailureAuditor rateLimiter;

	public OAuth2AwareSuccessHandler(LoginFailureAuditor auditor) {
		this.rateLimiter = auditor;
	}

	/**
	 * Handles successful authentication events.
	 * <p>
	 * Constructs a unique key from the username and client IP address,
	 * resets any recorded login failures via the {@link LoginFailureAuditor},
	 * and delegates to the default success handler to complete the redirect.
	 *
	 * @param request the {@link HttpServletRequest} during authentication
	 * @param response the {@link HttpServletResponse} used for redirect
	 * @param authentication the {@link Authentication} object representing the authenticated user
	 * @throws IOException if an error occurs during redirect
	 * @throws ServletException if an error occurs during request dispatch
	 */
	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
		String username = request.getParameter("username");
		String ip = request.getRemoteAddr();
		String key = username + "|" + ip;

		rateLimiter.resetFailures(key);

		super.onAuthenticationSuccess(request, response, authentication);

	}
}
