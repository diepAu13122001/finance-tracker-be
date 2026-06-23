package com.diepau1312.financeTrackerBE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling // ← THÊM để @Scheduled chạy được
public class FinanceTrackerBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(com.diepau1312.financeTrackerBE.FinanceTrackerBeApplication.class, args);
	}

}
