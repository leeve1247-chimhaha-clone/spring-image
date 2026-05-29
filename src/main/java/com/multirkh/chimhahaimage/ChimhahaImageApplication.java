package com.multirkh.chimhahaimage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChimhahaImageApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChimhahaImageApplication.class, args);
    }
}
