package nidam.tokengenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TokenGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TokenGeneratorApplication.class, args);
	}

}
