package dev.xiaohanglyu.paymentservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(LabProperties.class)
public class PaymentServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    /** Logs the version, as a real service does. The active faults are deliberately not
     *  logged: they stand for code in a release, and no real log names its own bug. */
    @Bean
    ApplicationRunner announceVersion(LabProperties properties) {
        return args -> log.info("payment-service {} ready", properties.version());
    }
}
