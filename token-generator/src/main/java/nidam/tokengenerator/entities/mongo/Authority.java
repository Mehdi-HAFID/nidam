package nidam.tokengenerator.entities.mongo;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "authority")
public class Authority {

	@Id
	private ObjectId id;

	private String name;

	public Authority() {

	}

	public Authority(String name) {
		this.name = name;
	}

	public ObjectId getId() {
		return id;
	}

	public void setId(ObjectId id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "Authority{" + "id=" + id + ", name='" + name + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		Authority authority = (Authority) o;
		return id.equals(authority.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
