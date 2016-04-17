package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LetWinnersRunApplication implements CommandLineRunner {
	
	private static final Logger log = LoggerFactory.getLogger(LetWinnersRunApplication.class);


	public static void main(String[] args) {
		SpringApplication.run(LetWinnersRunApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		// TODO Auto-generated method stub
		
	}
}
