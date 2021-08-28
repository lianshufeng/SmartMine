package com.github.smartmine;

import lombok.SneakyThrows;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan("com.github.smartmine.core")
@SpringBootApplication
public class SmartMineApplication {

    @SneakyThrows
    public static void main(String[] args) {
        SpringApplication.run(SmartMineApplication.class, args);
    }

}
