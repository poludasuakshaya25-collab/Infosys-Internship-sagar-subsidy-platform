package com.platform.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<String> executeBeneficiaryRegistration(@RequestBody UserRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            return ResponseEntity.badRequest().body("Registration Failed: Email is already registered!");
        }

        try {
            User user = new User();
            user.setEmail(dto.getEmail());
            user.setPassword(dto.getPassword());
            user.setRoles(new HashSet<>());

            Beneficiary beneficiary = new Beneficiary();
            beneficiary.setGovernmentId(dto.getGovernmentId());
            beneficiary.setContactNumber(normalizeContactNumber(dto.getContactNumber()));
            beneficiary.setAge(dto.getAge());
            beneficiary.setAddress(dto.getAddress());
            beneficiary.setSchemeName(dto.getSchemeName());
            beneficiary.setIncome(dto.getIncome());
            beneficiary.setOccupation(dto.getOccupation());
            beneficiary.setUser(user);
            user.setBeneficiary(beneficiary);

            userService.registerUser(user);
            return ResponseEntity.ok("User registration processed successfully through API backend.");
        } catch (UserRegistrationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private String normalizeContactNumber(String value) {
        if (value == null) return null;
        String phone = value.trim().replaceAll("[\\s-]", "");
        if (phone.startsWith("+91")) phone = phone.substring(3);
        return phone;
    }
}
