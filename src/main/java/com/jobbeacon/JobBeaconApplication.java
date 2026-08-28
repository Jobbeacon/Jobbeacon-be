package com.jobbeacon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class JobBeaconApplication {

  public static void main(String[] args) {
    SpringApplication.run(JobBeaconApplication.class, args);
  }
}
