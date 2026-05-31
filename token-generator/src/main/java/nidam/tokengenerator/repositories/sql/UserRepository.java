package nidam.tokengenerator.repositories.sql;

import nidam.tokengenerator.entities.sql.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "sql", matchIfMissing = true)
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findUserByEmail(String email);
}