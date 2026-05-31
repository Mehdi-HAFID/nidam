package nidam.nidam.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Demo REST controller used to validate method-level security using {@link PreAuthorize}.
 *
 * <p>This controller exposes endpoints protected by authority-based access control
 * to demonstrate how JWT claims are mapped to Spring Security authorities.</p>
 *
 * <ul>
 *   <li><b>/demo</b> — Accessible if the caller has either {@code manage-users}
 *       or {@code manage-projects} authority.</li>
 *   <li><b>/top-secret</b> — Accessible only if the caller has {@code top-secret} authority.</li>
 * </ul>
 *
 * <p>These endpoints are typically used for testing and verifying that the resource server
 * correctly enforces authorization rules based on JWT contents.</p>
 */
@RestController
public class DemoController {

//	manage-users,manage-projects
	/**
	 * Endpoint accessible to users with either {@code manage-users} or {@code manage-projects}.
	 *
	 * <p>Returns the full {@link JwtAuthenticationToken}, allowing inspection of:
	 * <ul>
	 *   <li>JWT claims</li>
	 *   <li>Granted authorities</li>
	 *   <li>Authentication details</li>
	 * </ul>
	 * </p>
	 *
	 * @param jwt the authenticated JWT token (injected by Spring Security)
	 * @return the {@link JwtAuthenticationToken} of the current user
	 */
	@GetMapping("/demo")
	@PreAuthorize( "hasAuthority('manage-users') or hasAuthority('manage-projects')")
	public JwtAuthenticationToken allowedResource(JwtAuthenticationToken jwt) { // parent class Authentication and Principal
		return jwt;
	}

	/**
	 * Highly restricted endpoint requiring {@code top-secret} authority.
	 *
	 * <p>Used to validate access denial behavior when insufficient permissions are present.</p>
	 *
	 * @return a sensitive message if access is granted
	 */
	@GetMapping("/top-secret")
	@PreAuthorize( "hasAuthority('top-secret')")
	public String forbiddenResource() {
		return "Top secret information";
	}
}