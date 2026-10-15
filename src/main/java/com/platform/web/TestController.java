package com.platform.web;

//package org.example.subsidyplatformtest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @GetMapping("/test")
    public String checkApp() {
        return "Spring Boot application is running successfully!";
    }
}
