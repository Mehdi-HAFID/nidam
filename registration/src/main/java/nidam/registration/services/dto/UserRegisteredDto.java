package nidam.registration.services.dto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class UserRegisteredDto {

	private BigInteger id;

	private String email;

	private List<String> authorities = new ArrayList<>();
	private List<String> roles = new ArrayList<>();

	public BigInteger getId() {
		return id;
	}

	public void setId(BigInteger id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public List<String> getAuthorities() {
		return authorities;
	}

	public void setAuthorities(List<String> authorities) {
		this.authorities = authorities;
	}

	public List<String> getRoles() {
		return roles;
	}

	public void setRoles(List<String> roles) {
		this.roles = roles;
	}

	@Override
	public String toString() {
		return "UserRegisteredDto{" +
				"id=" + id +
				", email='" + email + '\'' +
				", authorities=" + authorities +
				", roles=" + roles +
				'}';
	}
}
