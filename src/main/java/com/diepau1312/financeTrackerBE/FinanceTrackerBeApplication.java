package com.diepau1312.financeTrackerBE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FinanceTrackerBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(com.diepau1312.financeTrackerBE.FinanceTrackerBeApplication.class, args);
	}

}
