package com.easyeat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(com.easyeat.order.config.MercadoPagoProperties.class)
public class EasyEatApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyEatApplication.class, args);
    }
}
