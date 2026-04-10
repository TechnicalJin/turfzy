package com.turfzy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TurfzyBackendApplication {

	private static final Logger log = LoggerFactory.getLogger(TurfzyBackendApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(TurfzyBackendApplication.class, args);
		log.info("🚀 Turfzy Backend started successfully!");
	}

}
