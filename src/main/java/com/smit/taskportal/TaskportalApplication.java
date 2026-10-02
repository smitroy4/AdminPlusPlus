package com.smit.taskportal;

import com.smit.taskportal.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class TaskportalApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskportalApplication.class, args);
    }

}
