package com.easyeat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EasyEatApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyEatApplication.class, args);
    }
}
