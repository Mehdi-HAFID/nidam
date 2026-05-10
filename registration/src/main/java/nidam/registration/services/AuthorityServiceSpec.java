package nidam.registration.services;

/**
 * Contract for authority initialization logic.
 *
 * <p>Provides a common abstraction for populating authorities
 * regardless of the underlying persistence mechanism (SQL or MongoDB).</p>
 *
 * <p>Implementations are conditionally loaded based on the
 * {@code nidam.persistence-mode} property.</p>
 */
public interface AuthorityServiceSpec {

	/**
	 * Ensures that all configured authorities are persisted.
	 *
	 * <p>Missing authorities are inserted, while existing ones are skipped.</p>
	 */
	void addToDatabase();
}
