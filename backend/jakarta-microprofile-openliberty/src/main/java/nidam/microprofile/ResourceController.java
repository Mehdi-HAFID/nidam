package nidam.microprofile;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/")
public class ResourceController {

	@Context
	SecurityContext securityContext;

	@GET
	@Path("me")
	@PermitAll
	public Response getMe() {
		Principal principal = securityContext.getUserPrincipal();

		if (!(principal instanceof JsonWebToken jwt)) {
			return Response.ok(new UserInfoDto("", "", List.of(), Long.MAX_VALUE)).build();
		}

		String username = jwt.getName();

		String email = jwt.getClaim("email");
		if (email == null) {
			email = "";
		}

		@SuppressWarnings("unchecked")
		List<String> authorities = jwt.getClaim("authorities") != null ? (List<String>) jwt.getClaim("authorities") : List.of();

		Long exp = jwt.getExpirationTime();

		return Response.ok(new UserInfoDto(username, email, authorities, exp)).build();
	}

	@GET
	@Path("demo")
	@RolesAllowed({"manage-users", "manage-projects"})
	public Response demo() {
		JsonWebToken jwt = (JsonWebToken) securityContext.getUserPrincipal();

		Map<String, Object> authentication = new LinkedHashMap<>();

		authentication.put("authenticated", true);
		authentication.put("name", jwt.getName());
		authentication.put("token", jwt.getRawToken());
		authentication.put("claims", getClaims(jwt));
		authentication.put("authorities", jwt.getClaim("authorities"));

		return Response.ok(authentication).build();
//		return Response.ok(jwt).build();
	}

	@GET
	@Path("top-secret")
	@RolesAllowed("top-secret")
	public String topSecret() {
		return "Top secret information";
	}

	private Map<String, Object> getClaims(JsonWebToken jwt) {

		Map<String, Object> claims = new LinkedHashMap<>();

		for (String claimName : jwt.getClaimNames()) {
			claims.put(claimName, jwt.getClaim(claimName));
		}

		return claims;
	}

	public record UserInfoDto(String username, String email, List<String> authorities, Long exp) { }
}
