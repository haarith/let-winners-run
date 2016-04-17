package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class LetWinnersRunApplication implements CommandLineRunner {
	
	private static final Logger log = LoggerFactory.getLogger(LetWinnersRunApplication.class);
	
	@Autowired
    JdbcTemplate jdbcTemplate;


	public static void main(String[] args) {
		SpringApplication.run(LetWinnersRunApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		
		log.info("Creating tables");
		
//		jdbcTemplate.execute("DROP TABLE trades IF EXISTS");
		int numOfTrades = jdbcTemplate.queryForObject("select count(*) from trades", Integer.class);
		log.info("Number of trades in the input file = " + numOfTrades);
		
	}
}
