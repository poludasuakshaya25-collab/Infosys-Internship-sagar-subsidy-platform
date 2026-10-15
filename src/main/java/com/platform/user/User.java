package com.platform.user;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(unique = true)
    private String username;

    private String fullName;
    private String phoneNumber;
    private String assignedState;
    private String assignedDistrict;

    // Null is treated as ACTIVE for Milestone 1-3 legacy/test accounts.
    // Newly onboarded officers explicitly start as PENDING_APPROVAL.
    private String accountStatus;

    // 🔍 FIXED: Changing this collection initialization to a mutable HashSet allows Hibernate to write to your user_roles table!
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();

    // Per-account read receipts for application and reminder alerts.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_notification_reads", joinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_key"}))
    @Column(name = "event_key", nullable = false, length = 160)
    private Set<String> readNotificationKeys = new HashSet<>();


    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Beneficiary beneficiary;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getAssignedState() { return assignedState; }
    public void setAssignedState(String assignedState) { this.assignedState = assignedState; }
    public String getAssignedDistrict() { return assignedDistrict; }
    public void setAssignedDistrict(String assignedDistrict) { this.assignedDistrict = assignedDistrict; }
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
    public boolean isLoginAllowed() {
        return accountStatus == null || accountStatus.isBlank() || "ACTIVE".equals(accountStatus);
    }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public Set<String> getReadNotificationKeys() { return readNotificationKeys; }
    public void setReadNotificationKeys(Set<String> keys) { this.readNotificationKeys = keys; }
    public Beneficiary getBeneficiary() { return beneficiary; }
    public void setBeneficiary(Beneficiary beneficiary) { this.beneficiary = beneficiary; }
}
