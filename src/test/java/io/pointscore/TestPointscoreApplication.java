package io.pointscore;

import org.springframework.boot.SpringApplication;

public class TestPointscoreApplication {

	public static void main(String[] args) {
		SpringApplication.from(PointscoreApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
