package io.github.ccweixiao.datastoria.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.github.ccweixiao.datastoria")
public class DatastoriaServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(DatastoriaServerApplication.class, args);
  }
}
