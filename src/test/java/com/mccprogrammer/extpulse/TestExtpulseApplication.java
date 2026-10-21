package com.mccprogrammer.extpulse;

import org.springframework.boot.SpringApplication;

public class TestExtpulseApplication {

	public static void main(String[] args) {
		SpringApplication.from(ExtpulseApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
