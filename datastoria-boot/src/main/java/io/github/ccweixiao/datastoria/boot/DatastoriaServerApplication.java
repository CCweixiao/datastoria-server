package io.github.ccweixiao.datastoria.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.github.ccweixiao.datastoria")
@EnableScheduling
public class DatastoriaServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(DatastoriaServerApplication.class, args);
  }
}
