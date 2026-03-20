package com.caseware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DataEventAiCasewareApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataEventAiCasewareApplication.class, args);
    }

}
