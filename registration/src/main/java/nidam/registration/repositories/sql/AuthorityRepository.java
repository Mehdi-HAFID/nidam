package nidam.registration.repositories.sql;

import nidam.registration.entities.sql.Authority;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for {@code Authority} entities.
 *
 * <p>This repository is conditionally loaded when:
 * <pre>
 * nidam.persistence-mode=sql
 * </pre>
 * or when the property is not defined (default behavior).
 *
 * <p>Uses a numeric identifier ({@code Long}) typical for relational databases.
 *
 * <p>Provides query methods for:
 * <ul>
 *   <li>Finding an authority by its name</li>
 * </ul>
 *
 * <p>This repository is part of the SQL persistence implementation and is mutually
 * exclusive with the MongoDB-based {@code AuthorityRepository}.
 */
@Repository
@ConditionalOnProperty(name = "nidam.persistence-mode", havingValue = "sql", matchIfMissing = true)
public interface AuthorityRepository extends JpaRepository<Authority, Long> {
	Authority findAuthorityByName(String name);
}
