package com.example.payment;

import org.springframework.boot.SpringApplication;

public class TestPaymentChallengeApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaymentChallengeApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
