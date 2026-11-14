package com.syed.gatekeeper.management.controller;

import com.syed.gatekeeper.management.dto.requestDto.OrganizationRequest;
import com.syed.gatekeeper.management.dto.requestDto.OrganizationStatusRequest;
import com.syed.gatekeeper.management.dto.requestDto.TenantAdminRequest;
import com.syed.gatekeeper.management.dto.responseDto.OrganizationResponse;
import com.syed.gatekeeper.management.dto.responseDto.TenantAdminResponse;
import com.syed.gatekeeper.management.service.OrganizationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/platform/organizations")
public class PlatformOrganizationController {

    private final OrganizationService organizationService;


    // ==================== CREATE ====================

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(
            @Valid @RequestBody OrganizationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.create(request));
    }


    // ==================== READ ALL ====================

    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> findAll() {

        return ResponseEntity.ok(organizationService.findAll());
    }


    // ==================== READ BY ID ====================

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(organizationService.findById(id));
    }


    // ==================== UPDATE ====================

    @PutMapping("/{id}")
    public ResponseEntity<OrganizationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody OrganizationRequest request) {

        return ResponseEntity.ok(
                organizationService.update(id, request)
        );
    }


    // ==================== CHANGE STATUS ====================

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrganizationResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrganizationStatusRequest request) {

        return ResponseEntity.ok(
                organizationService.changeStatus(id, request)
        );
    }


    // ==================== SOFT DELETE ====================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {

        organizationService.delete(id);

        return ResponseEntity.noContent().build();
    }


    // CRUD --> Tenant Admins
    @PostMapping("/{organizationId}/tenant-admin")
    public ResponseEntity<TenantAdminResponse> createTenantAdmin(@PathVariable Long organizationId,
                                                                 @Valid @RequestBody TenantAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createTenantAdmin(organizationId, request));
    }

    @GetMapping("/{organizationId}/tenant-admin")
    public ResponseEntity<TenantAdminResponse> getTenantAdmin(@PathVariable Long organizationId) {
        return ResponseEntity.ok(organizationService.getTenantAdmin(organizationId));
    }

    @PutMapping("/{organizationId}/tenant-admin")
    public ResponseEntity<TenantAdminResponse> updateTenantAdmin(@PathVariable Long organizationId,
                                                                 @Valid @RequestBody TenantAdminRequest request) {
        return ResponseEntity.ok(organizationService.updateTenantAdmin(organizationId, request));
    }

    @DeleteMapping("/{organizationId}/tenant-admin")
    public ResponseEntity<Void> deleteTenantAdmin(@PathVariable Long organizationId) {
        organizationService.deleteTenantAdmin(organizationId);
        return ResponseEntity.noContent().build();
    }
}