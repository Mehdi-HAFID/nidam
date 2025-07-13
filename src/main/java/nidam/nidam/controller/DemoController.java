package nidam.nidam.controller;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

	@GetMapping("/demo")
	public JwtAuthenticationToken demo(JwtAuthenticationToken jwt) { // parent class Authentication a
		return jwt;
	}
}