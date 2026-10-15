package com.platform.application;

//package org.example.subsidyplatformtest;

import java.util.Map;

public class Eligibility {

    private Long userId;
    private boolean eligible;
    private Map<String, String> criteria;
    private String message;

    public Eligibility(Long userId, boolean eligible,
                       Map<String, String> criteria,
                       String message) {
        this.userId = userId;
        this.eligible = eligible;
        this.criteria = criteria;
        this.message = message;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isEligible() {
        return eligible;
    }

    public Map<String, String> getCriteria() {
        return criteria;
    }

    public String getMessage() {
        return message;
    }
}
