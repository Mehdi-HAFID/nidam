package nidam.registration.repositories.sql;

import nidam.registration.entities.sql.Role;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "sql", matchIfMissing = true)
public interface RoleRepository extends JpaRepository<Role, Long> {
	public Role findRoleByName(String name);
}
