package com.platform.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserRegistrationDto {

    @NotBlank(message = "Full name cannot be empty")
    @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters")
    private String fullName;

    @NotBlank(message = "Email cannot be empty")
    @Pattern(
            regexp = "^[a-zA-Z0-9._%+-]+@(gmail|yahoo|outlook|hotmail)\\.com$",
            message = "Please enter a valid public email (e.g., citizen@gmail.com)"
    )
    private String email;

    @NotBlank(message = "Password cannot be empty")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$",
            message = "Password must be at least 8 characters and include uppercase, lowercase, number and special character"
    )
    private String password;

    @NotBlank(message = "Aadhaar number cannot be empty")
    @Pattern(
            regexp = "^[0-9]{12}$",
            message = "Aadhaar number must contain exactly 12 digits"
    )
    private String governmentId;

    @NotBlank(message = "Contact number cannot be empty")
    @Pattern(
            regexp = "^(?:[6-9][0-9]{9}|\\+91[6-9][0-9]{9})$",
            message = "Enter a valid 10-digit mobile number, optionally prefixed with +91"
    )
    private String contactNumber;

    @Min(value = 18, message = "Age must be at least 18 years")
    @Max(value = 100, message = "Please enter a valid age under 100")
    private int age;

    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "District is required")
    private String district;

    @NotBlank(message = "Gender is required")
    private String gender;

    @NotBlank(message = "Disability status is required")
    private String disabilityStatus;

    @NotBlank(message = "Residence type is required")
    private String residenceType;

    @NotBlank(message = "Residential address is required")
    @Size(min = 5, max = 150, message = "Address must be between 5 and 150 characters")
    private String address;

    // Scheme is selected when the citizen actually applies. It is not required at registration.
    private String schemeName;

    @Min(value = 0, message = "Income cannot be negative")
    private double income;

    @NotBlank(message = "Occupation is required")
    private String occupation;

    // Getters and Setters
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getGovernmentId() { return governmentId; }
    public void setGovernmentId(String governmentId) { this.governmentId = governmentId; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getDisabilityStatus() { return disabilityStatus; }
    public void setDisabilityStatus(String disabilityStatus) { this.disabilityStatus = disabilityStatus; }
    public String getResidenceType() { return residenceType; }
    public void setResidenceType(String residenceType) { this.residenceType = residenceType; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public double getIncome() { return income; }
    public void setIncome(double income) { this.income = income; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
}



//package com.platform.user;
//
////package org.example.subsidyplatformtest;
//
//import jakarta.validation.constraints.Max;
//import jakarta.validation.constraints.Min;
//import jakarta.validation.constraints.NotBlank;
//import jakarta.validation.constraints.Pattern;
//import jakarta.validation.constraints.Size;
//
//public class UserRegistrationDto {
//
//    @NotBlank(message = "Email cannot be empty")
//    @Pattern(
//            // FIX: Allows normal email strings (with or without digits) from common public domains
//            regexp = "^[a-zA-Z0-9._%+-]+@(gmail|yahoo|outlook|hotmail)\\.com$",
//            message = "Please enter a valid, standard public email account (e.g., citizen@gmail.com)"
//    )
//    private String email;
//
//
//
//    @NotBlank(message = "Password cannot be empty")
//    private String password;
//
//
//    @NotBlank(message = "Government ID cannot be empty")
//    @Pattern(
//            regexp = "^([0-9]{12}|[A-Z]{5}[0-9]{4}[A-Z]{1})$",
//            message = "Must be a 12-digit Aadhaar Number or a valid PAN Card (e.g., ABCDE1234F)"
//    )
//    private String governmentId;
//
//
//    @NotBlank(message = "Contact number cannot be empty")
//    @Pattern(
//            regexp = "^[6-9][0-9]{9}$",
//            message = "Contact number must be a valid 10-digit mobile number starting with 6-9"
//    )
//    private String contactNumber;
//
//
//    @Min(value = 18, message = "Age must be at least 18 years to enroll")
//    @Max(value = 100, message = "Please enter a valid age under 100")
//    private int age;
//
//
//    @NotBlank(message = "Residential address is required")
//    @Size(
//            min = 5,
//            max = 150,
//            message = "Address must be between 5 and 150 characters"
//    )
//    private String address;
//
//
//    @NotBlank(message = "Please select a specific subsidy scheme")
//    private String schemeName;
//
//
//    // NEW: Annual income
//    @Min(value = 0, message = "Income cannot be negative")
//    private double income;
//
//
//    // NEW: Occupation
//    @NotBlank(message = "Occupation is required")
//    private String occupation;
//
//
//    // =========================
//    // GETTERS AND SETTERS
//    // =========================
//
//    public String getEmail() {
//        return email;
//    }
//
//    public void setEmail(String email) {
//        this.email = email;
//    }
//
//
//    public String getPassword() {
//        return password;
//    }
//
//    public void setPassword(String password) {
//        this.password = password;
//    }
//
//
//    public String getGovernmentId() {
//        return governmentId;
//    }
//
//    public void setGovernmentId(String governmentId) {
//        this.governmentId = governmentId;
//    }
//
//
//    public String getContactNumber() {
//        return contactNumber;
//    }
//
//    public void setContactNumber(String contactNumber) {
//        this.contactNumber = contactNumber;
//    }
//
//
//    public int getAge() {
//        return age;
//    }
//
//    public void setAge(int age) {
//        this.age = age;
//    }
//
//
//    public String getAddress() {
//        return address;
//    }
//
//    public void setAddress(String address) {
//        this.address = address;
//    }
//
//
//    public String getSchemeName() {
//        return schemeName;
//    }
//
//    public void setSchemeName(String schemeName) {
//        this.schemeName = schemeName;
//    }
//
//
//    // NEW: Income getter/setter
//    public double getIncome() {
//        return income;
//    }
//
//    public void setIncome(double income) {
//        this.income = income;
//    }
//
//
//    // NEW: Occupation getter/setter
//    public String getOccupation() {
//        return occupation;
//    }
//
//    public void setOccupation(String occupation) {
//        this.occupation = occupation;
//    }
//}