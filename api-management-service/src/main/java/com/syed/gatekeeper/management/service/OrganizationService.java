package com.syed.gatekeeper.management.service;

import com.syed.gatekeeper.management.dto.requestDto.OrganizationRequest;
import com.syed.gatekeeper.management.dto.requestDto.OrganizationStatusRequest;
import com.syed.gatekeeper.management.dto.requestDto.TenantAdminRequest;

import com.syed.gatekeeper.management.dto.responseDto.OrganizationResponse;
import com.syed.gatekeeper.management.dto.responseDto.TenantAdminResponse;

import com.syed.gatekeeper.management.entity.ManagementUser;
import com.syed.gatekeeper.management.entity.Organization;
import com.syed.gatekeeper.management.entity.OrganizationStatus;
import com.syed.gatekeeper.management.entity.Role;

import com.syed.gatekeeper.management.exception.ApiException;

import com.syed.gatekeeper.management.repository.ManagementUserRepository;
import com.syed.gatekeeper.management.repository.OrganizationRepository;
import com.syed.gatekeeper.management.repository.RoleRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class OrganizationService {

    // ==================== DEPENDENCIES ====================

    private final OrganizationRepository organizationRepository;
    private final ManagementUserRepository managementUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;


    // ========================================================
    // ORGANIZATION CRUD OPERATIONS
    // ========================================================


    // ==================== CREATE ORGANIZATION ====================

    @Transactional
    public OrganizationResponse create(@Valid OrganizationRequest request) {

        checkUnique(request.getName(), request.getSlug(), null);

        Organization organization = new Organization();

        organization.setName(request.getName());
        organization.setSlug(request.getSlug());
        organization.setStatus(OrganizationStatus.ACTIVE);

        LocalDateTime now = LocalDateTime.now();

        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);

        organizationRepository.save(organization);

        return OrganizationResponse.from(organization);
    }


    // ==================== READ ALL ORGANIZATIONS ====================

    @Transactional(readOnly = true)
    public List<OrganizationResponse> findAll() {

        List<OrganizationResponse> response = new ArrayList<>();

        for (Organization organization :
                organizationRepository.findAllByStatusNot(OrganizationStatus.DELETED)) {

            response.add(OrganizationResponse.from(organization));
        }

        return response;
    }


    // ==================== READ ORGANIZATION BY ID ====================

    @Transactional(readOnly = true)
    public OrganizationResponse findById(Long id) {

        return OrganizationResponse.from(getOrganization(id));
    }


    // ==================== UPDATE ORGANIZATION ====================

    @Transactional
    public OrganizationResponse update(Long id, @Valid OrganizationRequest request) {

        Organization organization = getOrganization(id);

        checkUnique(request.getName(), request.getSlug(), id);

        organization.setName(request.getName());
        organization.setSlug(request.getSlug());
        organization.setUpdatedAt(LocalDateTime.now());

        return OrganizationResponse.from(organization);
    }


    // ==================== CHANGE ORGANIZATION STATUS ====================

    @Transactional
    public OrganizationResponse changeStatus(
            Long id,
            OrganizationStatusRequest request) {

        Organization organization = getOrganization(id);

        if (request.getStatus() == OrganizationStatus.DELETED) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Use DELETE to soft delete an organization"
            );
        }

        organization.setStatus(request.getStatus());
        organization.setUpdatedAt(LocalDateTime.now());

        return OrganizationResponse.from(organization);
    }


    // ==================== SOFT DELETE ORGANIZATION ====================

    @Transactional
    public void delete(Long id) {

        Organization organization = getOrganization(id);

        LocalDateTime now = LocalDateTime.now();

        organization.setStatus(OrganizationStatus.DELETED);
        organization.setDeletedAt(now);
        organization.setUpdatedAt(now);
    }


    // ========================================================
    // TENANT ADMIN CRUD OPERATIONS
    // ========================================================


    // ==================== CREATE TENANT ADMIN ====================

    @Transactional
    public TenantAdminResponse createTenantAdmin(
            Long organizationId,
            @Valid TenantAdminRequest request) {

        Organization organization = getOrganization(organizationId);

        // Check if the organization already has a tenant admin
        if (managementUserRepository.findByOrganizationId(organizationId).isPresent()) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "This organization already has a tenant administrator"
            );
        }

        // Check if username already exists
        if (managementUserRepository.existsByUsername(request.getUsername())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        // Get TENANT_ADMIN role
        Role role = roleRepository.findByName("TENANT_ADMIN")
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "TENANT_ADMIN role is missing"
                        )
                );

        // Create management user
        ManagementUser user = new ManagementUser();

        user.setOrganization(organization);
        user.setRole(role);
        user.setUsername(request.getUsername());

        // Hash password before storing
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        user.setEnabled(request.isEnabled());

        LocalDateTime now = LocalDateTime.now();

        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        // Save tenant admin
        managementUserRepository.save(user);

        return TenantAdminResponse.from(user);
    }


    // ==================== GET TENANT ADMIN ====================

    @Transactional(readOnly = true)
    public TenantAdminResponse getTenantAdmin(Long organizationId) {

        return TenantAdminResponse.from(
                getTenantAdminEntity(organizationId)
        );
    }


    // ==================== UPDATE TENANT ADMIN ====================

    @Transactional
    public TenantAdminResponse updateTenantAdmin(
            Long organizationId,
            @Valid TenantAdminRequest request) {

        ManagementUser user = getTenantAdminEntity(organizationId);

        // Check username uniqueness if username is being changed
        if (!user.getUsername().equals(request.getUsername())
                && managementUserRepository.existsByUsername(request.getUsername())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Username already exists"
            );
        }

        // Update user details
        user.setUsername(request.getUsername());

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        user.setEnabled(request.isEnabled());
        user.setUpdatedAt(LocalDateTime.now());

        return TenantAdminResponse.from(user);
    }


    // ==================== DELETE TENANT ADMIN ====================

    @Transactional
    public void deleteTenantAdmin(Long organizationId) {

        ManagementUser user = getTenantAdminEntity(organizationId);

        managementUserRepository.delete(user);
    }


    // ========================================================
    // HELPER METHODS
    // ========================================================


    // ==================== HELPER: GET ORGANIZATION ====================

    private Organization getOrganization(Long id) {

        return organizationRepository
                .findByIdAndStatusNot(id, OrganizationStatus.DELETED)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                "Organization not found"
                        )
                );
    }


    // ==================== HELPER: CHECK UNIQUENESS ====================

    private void checkUnique(String name, String slug, Long currentId) {

        // Check organization name
        if (organizationRepository.existsByNameIgnoreCase(name)) {

            Organization current = currentId == null
                    ? null
                    : organizationRepository.findById(currentId).orElse(null);

            if (current == null ||
                    !current.getName().equalsIgnoreCase(name)) {

                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Organization name already exists"
                );
            }
        }

        // Check organization slug
        if (organizationRepository.existsBySlugIgnoreCase(slug)) {

            Organization current = currentId == null
                    ? null
                    : organizationRepository.findById(currentId).orElse(null);

            if (current == null ||
                    !current.getSlug().equalsIgnoreCase(slug)) {

                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Organization slug already exists"
                );
            }
        }
    }


    // ==================== HELPER: GET TENANT ADMIN ENTITY ====================

    private ManagementUser getTenantAdminEntity(Long organizationId) {

        // Verify organization exists and is not deleted
        getOrganization(organizationId);

        return managementUserRepository
                .findByOrganizationId(organizationId)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                "Tenant administrator not found"
                        )
                );
    }

}