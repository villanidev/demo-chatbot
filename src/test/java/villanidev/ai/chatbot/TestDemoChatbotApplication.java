package villanidev.ai.chatbot;

import org.springframework.boot.SpringApplication;

public class TestDemoChatbotApplication {

	public static void main(String[] args) {
		SpringApplication.from(ChatbotApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
