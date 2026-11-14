package com.syed.gatekeeper.management.repository;

import com.syed.gatekeeper.management.entity.Organization;
import com.syed.gatekeeper.management.entity.OrganizationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    // Check if organization name already exists
    boolean existsByNameIgnoreCase(String name);


    // Check if organization slug already exists
    boolean existsBySlugIgnoreCase(String slug);


    // Find all organizations except deleted ones
    List<Organization> findAllByStatusNot(OrganizationStatus status);


    // Find organization by ID, excluding deleted organizations
    Optional<Organization> findByIdAndStatusNot(
            Long id,
            OrganizationStatus status
    );
}