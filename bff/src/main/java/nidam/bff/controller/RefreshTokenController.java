//package nidam.bff.controller;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import org.springframework.http.*;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.server.ServerWebExchange;
//import reactor.core.publisher.Mono;
//
//import java.time.Duration;
//
//@RestController
//@RequestMapping
//public class RefreshTokenController {
//
//	private final WebClient webClient;
////	private final SecurityProps securityProps;
//
//	public RefreshTokenController(WebClient.Builder builder) {
////		this.securityProps = securityProps;
//		this.webClient = builder.baseUrl("http://localhost:7080/auth").build();
//	}
//
//	@PostMapping("/refreshToken")
//	public Mono<ResponseEntity<Void>> refreshToken(ServerWebExchange exchange) {
//		// 1. Get refresh token from secure cookie or session
//		HttpCookie cookie = exchange.getRequest().getCookies().getFirst("refresh_token");
//		if (cookie == null) {
//			return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
//		}
//
//		String refreshToken = cookie.getValue();
//
//		// 2. Send request to token endpoint
//		return webClient.post()
//				.uri("/oauth2/token")
//				.headers(headers -> {
//					headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//					headers.setBasicAuth("client", "secret");
//				})
//				.bodyValue("grant_type=refresh_token&refresh_token=" + refreshToken)
//				.exchangeToMono(response -> {
//					if (response.statusCode().is2xxSuccessful()) {
//						return response.bodyToMono(JsonNode.class)
//								.map(tokenResponse -> {
//									String newAccessToken = tokenResponse.get("access_token").asText();
//									String newRefreshToken = tokenResponse.get("refresh_token").asText();
//// 3. Set new tokens as HttpOnly cookies
//									ResponseCookie accessCookie = ResponseCookie.from("access_token", newAccessToken)
//											.httpOnly(true)
//											.path("/")
//											.maxAge(Duration.ofMinutes(15))
//											.build();
//
//									ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", newRefreshToken)
//											.httpOnly(true)
//											.path("/")
//											.maxAge(Duration.ofHours(12))
//											.build();
//
//									return ResponseEntity.ok()
//											.header(HttpHeaders.SET_COOKIE, accessCookie.toString())
//											.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
//											.build();
//								});
//					} else {
//						return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
//					}
//				});
//
//	}
//}
