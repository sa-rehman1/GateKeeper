package com.syed.gatekeeper.management.controller;

import com.syed.gatekeeper.management.dto.requestDto.TenantOrganizationRequest;
import com.syed.gatekeeper.management.dto.responseDto.OrganizationResponse;
import com.syed.gatekeeper.management.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/organization")
public class TenantOrganizationController {
    private OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<OrganizationResponse> getOwnOrganization(){
        return ResponseEntity.ok(organizationService.getOwnOrganization());
    }

    @GetMapping
    public ResponseEntity<OrganizationResponse> updateOwnOrganization(@Valid @RequestBody TenantOrganizationRequest request){
        return ResponseEntity.ok(organizationService.updateOwnOrganization(request));
    }
}
