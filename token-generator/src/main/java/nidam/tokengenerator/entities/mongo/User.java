package nidam.tokengenerator.entities.mongo;

import jakarta.validation.constraints.Email;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Document(collection = "users")
public class User {

	@Id
	private ObjectId id;

	@Email
	private String email;

	private String password;

	private List<ObjectId> authoritiesIds = new ArrayList<>();

	private boolean enabled;

	public User() {

	}

	public ObjectId getId() {
		return id;
	}

	public void setId(ObjectId id) {
		this.id = id;
	}

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

	public List<ObjectId> getAuthoritiesIds() {
		return authoritiesIds;
	}

	public void setAuthoritiesIds(List<ObjectId> authoritiesIds) {
		this.authoritiesIds = authoritiesIds;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public String toString() {
		return "User{" + "email='" + email + '\'' + ", enabled=" + enabled + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		User user = (User) o;
		return Objects.equals(id, user.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
