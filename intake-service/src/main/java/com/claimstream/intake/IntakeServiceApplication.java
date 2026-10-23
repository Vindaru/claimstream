package com.claimstream.intake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the Intake Service, the front door every claim enters through. */
@SpringBootApplication
public class IntakeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntakeServiceApplication.class, args);
    }
}
