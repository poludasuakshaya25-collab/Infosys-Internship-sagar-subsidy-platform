package org.example.subsidyplatformtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.platform.user.User;
import com.platform.user.UserRepository;
import java.util.Set;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "org.example.subsidyplatformtest",
        "com.platform"
})
@EntityScan(basePackages = {
        "com.platform"
})
@EnableJpaRepositories(basePackages = {
        "com.platform"
})
public class SubsidyPlatformTestApplication {

    @Bean
    CommandLineRunner initializeAdmin(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_EMAIL:}") String adminEmail,
            @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        return args -> {
            if (adminEmail.isBlank() || adminPassword.isBlank()) {
                System.out.println("Admin bootstrap skipped: ADMIN_EMAIL or ADMIN_PASSWORD not set.");
                return;
            }
            if (adminPassword.length() != 9) {
                throw new IllegalStateException("ADMIN_PASSWORD must be exactly 9 characters.");
            }
            if (users.existsByEmail(adminEmail.trim())) {
                System.out.println("Admin bootstrap: email already exists; account unchanged.");
                return;
            }
            User admin = new User();
            admin.setEmail(adminEmail.trim());
            admin.setUsername("sagaradmin");
            admin.setFullName("SAGAR Administrator");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setAccountStatus("ACTIVE");
            admin.setRoles(Set.of("ROLE_ADMIN"));
            users.save(admin);
            System.out.println("Admin bootstrap: administrator account created.");
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(
                SubsidyPlatformTestApplication.class,
                args
        );
    }
}


//package org.example.subsidyplatformtest;
//
//import org.springframework.boot.SpringApplication;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.boot.persistence.autoconfigure.EntityScan;
//import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
//
//@EnableScheduling
//@SpringBootApplication(scanBasePackages = {
//        "org.example.subsidyplatformtest",
//        "com.platform"
//})
//@EntityScan(basePackages = {
//        "com.platform"
//})
//@EnableJpaRepositories(basePackages = {
//        "com.platform"
//})
//public class SubsidyPlatformTestApplication {
//
//    public static void main(String[] args) {
//        SpringApplication.run(
//                SubsidyPlatformTestApplication.class,
//                args
//        );
//    }
//}