//package nidam.nidam.authentication;
//
//import org.springframework.core.convert.converter.Converter;
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.oauth2.jwt.Jwt;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.logging.Logger;
//
////@Component  TODO move from spring-addons-starter-oidc to this
//public class JwtAuthenticationConverter implements Converter<Jwt, CustomAuthentication> {
//	private Logger log = Logger.getLogger(JwtAuthenticationConverter.class.getName());
//
//	@Override
//	public CustomAuthentication convert(Jwt source) {
//		List<String> authorities2 = (ArrayList<String>) source.getClaims().get("authorities");
//		List<GrantedAuthority> authorities = authorities2.stream().map(authority -> (GrantedAuthority) () -> authority).toList();
//		log.info("authorities2: " + authorities2);
//		log.info("authorities: " + authorities);
//		return new CustomAuthentication(source, authorities, authorities2);
//	}
//
//}
