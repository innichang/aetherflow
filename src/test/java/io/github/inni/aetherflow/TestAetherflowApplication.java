package io.github.inni.aetherflow;

import org.springframework.boot.SpringApplication;

public class TestAetherflowApplication {

	public static void main(String[] args) {
		SpringApplication.from(AetherflowApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
