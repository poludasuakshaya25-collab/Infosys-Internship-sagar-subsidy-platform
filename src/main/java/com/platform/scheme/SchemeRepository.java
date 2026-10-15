package com.platform.scheme;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchemeRepository extends JpaRepository<Scheme, Long> {
    Optional<Scheme> findBySchemeName(String schemeName);
    Optional<Scheme> findBySchemeNameIgnoreCase(String schemeName);
}
