package xyz.rawmanoj.mrbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class MrBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(MrBankApplication.class, args);
    }

}
