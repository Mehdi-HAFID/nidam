package nidam.nidam.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
public class DemoController {
	private Logger log = Logger.getLogger(DemoController.class.getName());

	@GetMapping("/demo")
	public Authentication demo(Authentication a) {
//		log.info("a.getAuthorities(): " + a.getAuthorities());
//		log.info("((Jwt) a.getPrincipal()).getClaims(): " + ((Jwt) a.getPrincipal()).getClaims());
		log.info("((Jwt) a.getPrincipal()).getClaim('authorities'): " + ((Jwt) a.getPrincipal()).getClaim("authorities"));
//		log.info("a.getCredentials(): " + a.getCredentials());
//		log.info("a.getDetails(): " + a.getDetails());

		JwtAuthenticationToken cusAuth = (JwtAuthenticationToken) a; // TODO switch back to CustomAuthentication
		log.info("cusAuth.getAuthorities2(): " + cusAuth.getAuthorities());
//		log.info("cusAuth.getAuthorities(): " + cusAuth.getAuthorities().stream().map(ab -> ab.getAuthority()).toList());
		return a;
	}
}