package nidam.bff.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.InMemoryWebSessionStore;

@Configuration
@EnableScheduling
public class SessionMonitor {

	private final InMemoryWebSessionStore store;

	public SessionMonitor(InMemoryWebSessionStore store) {
		this.store = store;
	}

	@Scheduled(fixedRate = 60_000) // every minute
	public void logSessionStats() {
		int activeSessions = getActiveSessionCount();
		System.out.println("[SESSION-MONITOR] Active sessions: " + activeSessions);

		if (activeSessions > 0) {
			System.out.println("First Session in List. getAttributes : " + getFirstSession().getAttributes()
					+ ", getMaxIdleTime : " + getFirstSession().getMaxIdleTime() + ", isExpired - " + getFirstSession().isExpired());
		}
	}

	private int getActiveSessionCount() {
		return store.getSessions().size(); // internal field
	}

	private WebSession getFirstSession() {
		return store.getSessions().get(store.getSessions().keySet().stream().findFirst().get());
	}

	// For Benchmark, clean expired sessions
	@Scheduled(fixedRate = 60_000)
	public void monitorAndCleanup() {
		store.removeExpiredSessions();
		if (getActiveSessionCount() > 0) {
			System.out.println("[SESSION-MONITOR] after cleanup active sessions: " + getActiveSessionCount());
		}
	}
}
