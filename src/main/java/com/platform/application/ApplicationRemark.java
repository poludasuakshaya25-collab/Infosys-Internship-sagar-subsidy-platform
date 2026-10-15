package com.platform.application;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "application_remarks")
public class ApplicationRemark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private SubsidyApplication application;

    @Column(nullable = false, length = 80)
    private String stage;

    @Column(nullable = false, length = 80)
    private String actorRole;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 2000)
    private String remarks;

    @Column(nullable = false)
    private boolean visibleToApplicant = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SubsidyApplication getApplication() { return application; }
    public void setApplication(SubsidyApplication application) { this.application = application; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public boolean isVisibleToApplicant() { return visibleToApplicant; }
    public void setVisibleToApplicant(boolean visibleToApplicant) { this.visibleToApplicant = visibleToApplicant; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
