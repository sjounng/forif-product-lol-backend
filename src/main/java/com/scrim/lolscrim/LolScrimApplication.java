package com.scrim.lolscrim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LolScrimApplication {

	public static void main(String[] args) {
		SpringApplication.run(LolScrimApplication.class, args);
	}

}
