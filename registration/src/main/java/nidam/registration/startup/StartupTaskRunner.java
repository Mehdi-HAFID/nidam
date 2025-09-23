package nidam.registration.startup;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.List;

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
