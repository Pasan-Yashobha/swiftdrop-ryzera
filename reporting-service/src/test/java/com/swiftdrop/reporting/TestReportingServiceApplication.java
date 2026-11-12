package com.swiftdrop.reporting;

import org.springframework.boot.SpringApplication;

public class TestReportingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ReportingServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
