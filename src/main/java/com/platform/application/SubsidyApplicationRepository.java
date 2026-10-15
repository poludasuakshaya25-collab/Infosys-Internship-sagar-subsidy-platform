package com.platform.application;

import com.platform.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubsidyApplicationRepository extends JpaRepository<SubsidyApplication, Long> {

    List<SubsidyApplication> findByApplicationStatus(ApplicationStatus status);

    List<SubsidyApplication> findByUser(User user);

    List<SubsidyApplication> findByUserAndApplicationStatusIn(User user, List<ApplicationStatus> statuses);

    List<SubsidyApplication> findByApplicationStatusOrderByApplicationDateAsc(ApplicationStatus status);

    List<SubsidyApplication> findAllByOrderByApplicationDateDesc();

    long countByScheme(com.platform.scheme.Scheme scheme);

    Optional<SubsidyApplication> findTopByUserAndSchemeOrderByApplicationDateDesc(
            User user, com.platform.scheme.Scheme scheme);

    /**
     * Loads the relationships needed by the application-details and re-verification
     * pages in the same query. This prevents LazyInitializationException while
     * Thymeleaf renders the view.
     */
    @Query("select a from SubsidyApplication a " +
            "join fetch a.user u " +
            "left join fetch u.beneficiary " +
            "join fetch a.scheme s " +
            "where a.id = :id")
    Optional<SubsidyApplication> findDetailedById(@Param("id") Long id);
}
