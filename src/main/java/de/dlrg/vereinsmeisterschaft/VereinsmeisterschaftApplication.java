package de.dlrg.vereinsmeisterschaft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VereinsmeisterschaftApplication {
    public static void main(String[] args) {
        SpringApplication.run(VereinsmeisterschaftApplication.class, args);
    }
}
