package com.syed.gatekeeper.management.service;

import com.syed.gatekeeper.management.entity.Organization;
import com.syed.gatekeeper.management.entity.OrganizationStatus;
import com.syed.gatekeeper.management.exception.ApiException;
import com.syed.gatekeeper.management.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantContext {

    private final OrganizationRepository organizationRepository;

    public Organization getOrganization(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(!(authentication instanceof JwtAuthenticationToken)){
            throw new ApiException(HttpStatus.FORBIDDEN , "Tenant identity is missing");
        }

        Number organizationIdClaim = ((JwtAuthenticationToken) authentication).getToken().getClaim("organizationId");
        Long organizationId = organizationIdClaim == null ? null : organizationIdClaim.longValue();

        if(organizationId == null){
            throw new ApiException(HttpStatus.FORBIDDEN , "Tenant identity is missing");
        }

        Organization organization = organizationRepository.findById(organizationId).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Organization does not exist"));

        if (organization.getStatus() != OrganizationStatus.ACTIVE){
            throw new ApiException(HttpStatus.FORBIDDEN , "The Organization is not active");
        }

        return organization;

    }
}
