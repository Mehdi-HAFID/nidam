package nidam.registration.startup;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Executes all {@link StartupTask} beans at application startup.
 *
 * <p>This runner collects all registered {@link StartupTask} implementations,
 * sorts them, and executes them sequentially.</p>
 *
 * <p>Tasks can control their execution order via {@link Order}
 *
 * <p>This provides a simple and extensible mechanism for running
 * initialization logic (e.g., data seeding, validation) during startup.</p>
 */
@Component
public class StartupTaskRunner implements ApplicationRunner {

	private final List<StartupTask> startupTasks;

	public StartupTaskRunner(List<StartupTask> startupTasks) {
		this.startupTasks = startupTasks.stream().sorted(AnnotationAwareOrderComparator.INSTANCE).toList();
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		for (StartupTask task : startupTasks) {
			task.run();
		}
	}
}
