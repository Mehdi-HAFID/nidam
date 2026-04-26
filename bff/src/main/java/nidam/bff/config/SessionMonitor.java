package nidam.bff.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.InMemoryWebSessionStore;

import java.util.logging.Logger;

@Configuration
@EnableScheduling
public class SessionMonitor {

	private static final Logger log = Logger.getLogger(SessionMonitor.class.getName());
	private final InMemoryWebSessionStore store;

	public SessionMonitor(InMemoryWebSessionStore store) {
		this.store = store;
	}

	@Scheduled(fixedRate = 60_000) // every minute
	public void logSessionStats() {
		int activeSessions = getActiveSessionCount();

		if (activeSessions > 0) {
			log.info("[SESSION-MONITOR] Active sessions: " + activeSessions);
//			log.info("First Session in List. getAttributes : " + getFirstSession().getAttributes()
//					+ ", getMaxIdleTime : " + getFirstSession().getMaxIdleTime() + ", isExpired - " + getFirstSession().isExpired());
		}
	}

	private int getActiveSessionCount() {
		return store.getSessions().size(); // internal field
	}

	private WebSession getFirstSession() {
		return store.getSessions().get(store.getSessions().keySet().stream().findFirst().get());
	}
}
