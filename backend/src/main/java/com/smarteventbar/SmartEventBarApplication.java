package com.smarteventbar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartEventBarApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartEventBarApplication.class, args);
    }
}
