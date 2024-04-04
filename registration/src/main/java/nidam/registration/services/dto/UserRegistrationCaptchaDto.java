package nidam.registration.services.dto;

public class UserRegistrationCaptchaDto {

	private String email;

	private String password;

	private String recaptchaKey;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getRecaptchaKey() {
		return recaptchaKey;
	}

	public void setRecaptchaKey(String recaptchaKey) {
		this.recaptchaKey = recaptchaKey;
	}

	@Override
	public String toString() {
		return "UserRegistrationDto{" +
				"email='" + email + '\'' +
				", password='" + password + '\'' +
				'}';
	}
}
