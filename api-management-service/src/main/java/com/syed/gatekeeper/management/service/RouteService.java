package com.syed.gatekeeper.management.service;

import com.syed.gatekeeper.management.dto.requestDto.HeaderRuleRequest;
import com.syed.gatekeeper.management.dto.requestDto.RouteRequest;
import com.syed.gatekeeper.management.dto.requestDto.RouteStatusRequest;
import com.syed.gatekeeper.management.dto.responseDto.AuditRecordResponse;
import com.syed.gatekeeper.management.dto.responseDto.RouteResponse;
import com.syed.gatekeeper.management.dto.responseDto.RouteVersionResponse;
import com.syed.gatekeeper.management.entity.*;
import com.syed.gatekeeper.management.event.GatewayConfigurationEventService;
import com.syed.gatekeeper.management.exception.ApiException;
import com.syed.gatekeeper.management.repository.ApiRouteRepository;
import com.syed.gatekeeper.management.repository.ControlPlaneAuditRecordRepository;
import com.syed.gatekeeper.management.repository.RouteConfigurationVersionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final ApiRouteRepository apiRouteRepository;
    private final RouteConfigurationVersionRepository versionRepository;
    private final ControlPlaneAuditRecordRepository auditRecordRepository;
    private final TenantContext tenantContext;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final GatewayConfigurationEventService gatewayConfigurationEventService;

    @Transactional
    public RouteResponse create(@Valid RouteRequest request) {
        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();
        validate(request, organization);

        if (apiRouteRepository.existsByOrganizationIdAndRouteId(organization.getId(), request.getRouteId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Route ID already exists for this organization");
        }

        ApiRoute route = new ApiRoute();
        route.setOrganization(organization);
        route.setConfigurationVersion(1L);
        route.setCreatedAt(LocalDateTime.now());
        apply(route, request);

        apiRouteRepository.save(route);

        saveVersion(route, "CREATED");

        auditService.record(organization, "CREATE_ROUTE", "API_ROUTE",
                route.getRouteId(), startedAt, "Route configuration Version 1 created");

        return RouteResponse.from(route);
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> findAllForTenant() {
        Organization organization = tenantContext.getOrganization();
        return toResponses(apiRouteRepository.findAllByOrganizationId(organization.getId()));
    }

    @Transactional(readOnly = true)
    public RouteResponse findById(Long id) {
        Organization organization = tenantContext.getOrganization();
        return RouteResponse.from(getOwnedRoute(id, organization.getId()));
    }

    @Transactional
    public RouteResponse update(Long id, RouteRequest request) {
        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();
        ApiRoute route = getOwnedRoute(id, organization.getId());

        if (!route.getRouteId().equals(request.getRouteId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "routeId cannot be changed");
        }

        validate(request, organization);

        route.setConfigurationVersion(route.getConfigurationVersion() + 1);
        apply(route, request);

        saveVersion(route, "UPDATED");

        auditService.record(organization, "UPDATE_ROUTE", "API_ROUTE",
                route.getRouteId(), startedAt, "Route configuration version " +
                        route.getConfigurationVersion() + " created");

        return RouteResponse.from(route);
    }

    @Transactional
    public RouteResponse changeStatus(Long id, RouteStatusRequest request) {
        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();
        ApiRoute route = getOwnedRoute(id, organization.getId());
        route.setActive(request.getActive());
        route.setConfigurationVersion(route.getConfigurationVersion() + 1);
        route.setUpdatedAt(LocalDateTime.now());

        String changeType = request.getActive() ? "ACTIVATED" : "DEACTIVATED";
        String action = request.getActive() ? "ACTIVATE_ROUTE" : "DEACTIVATE_ROUTE";

        saveVersion(route, changeType);

        auditService.record(organization, action, "API_ROUTE", route.getRouteId(), startedAt,
                "Route configuration version " + route.getConfigurationVersion() + " created");

        return RouteResponse.from(route);
    }

    @Transactional
    public void delete(Long id) {
        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();
        ApiRoute route = getOwnedRoute(id, organization.getId());
        route.setConfigurationVersion(route.getConfigurationVersion() + 1);
        route.setUpdatedAt(LocalDateTime.now());

        saveVersion(route, "DELETED");

        auditService.record(organization, "DELETE_ROUTE", "API_ROUTE", route.getRouteId(), startedAt,
                "Final configuration saved as version " + route.getConfigurationVersion());

        apiRouteRepository.delete(route);
    }

    @Transactional(readOnly = true)
    public List<RouteVersionResponse> getVersions(String routeId) {
        Organization organization = tenantContext.getOrganization();

        List<RouteVersionResponse> response = new ArrayList<>();
        List<RouteConfigurationVersion> versions = versionRepository
                .findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(organization.getId(), routeId);

        for (RouteConfigurationVersion version : versions) {
            response.add(RouteVersionResponse.from(version));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> findAllActiveForGateway() {
        return toResponses(apiRouteRepository.findAllByActiveTrueAndOrganizationStatus(OrganizationStatus.ACTIVE));
    }

    private void apply(ApiRoute route, RouteRequest request) {
        route.setRouteId(request.getRouteId());
        route.setName(request.getName());
        route.setPathPattern(request.getPathPattern());
        route.setTargetBaseUrl(request.getTargetBaseUrl());
        route.setTargetPathPrefix(request.getTargetPathPrefix());
        route.setRequestsPerMinute(request.getRequestsPerMinute());
        route.setResponseTimeoutMs(request.getResponseTimeoutMs());
        route.setIdempotencyEnabled(request.isIdempotencyEnabled());
        route.setActive(request.isActive());
        route.setUpdatedAt(LocalDateTime.now());
        route.getAllowedMethods().clear();
        route.getAllowedMethods().addAll(normalizeMethods(request.getAllowedMethods()));

        route.getHeaderRules().clear();
        for (HeaderRuleRequest ruleRequest : request.getHeaderRules()) {
            RouteHeaderRule rule = new RouteHeaderRule();
            rule.setRoute(route);
            rule.setDirection(ruleRequest.getDirection().toUpperCase(Locale.ENGLISH));
            rule.setAction(ruleRequest.getAction().toUpperCase(Locale.ENGLISH));
            rule.setHeaderName(ruleRequest.getHeaderName());
            rule.setHeaderValue(ruleRequest.getHeaderValue());
            rule.setCreatedAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());
            route.getHeaderRules().add(rule);
        }
    }

    // HELPER METHODS
    private void validate(RouteRequest request, Organization organization) {
        String tenantPrefix = "/" + organization.getSlug() + "/";

        if (!request.getPathPattern().startsWith(tenantPrefix)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "pathPattern must start with the tenant prefix " + tenantPrefix);
        }

        if (!request.getTargetPathPrefix().startsWith("/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "targetPathPrefix must start with /");
        }

        try {
            URI uri = URI.create(request.getTargetBaseUrl());
            if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "targetBaseUrl must be a valid HTTP or HTTPS URL");
        }

        normalizeMethods(request.getAllowedMethods());

        for (HeaderRuleRequest rule : request.getHeaderRules()) {
            String direction = rule.getDirection().toUpperCase(Locale.ENGLISH);
            String action = rule.getAction().toUpperCase(Locale.ENGLISH);
            if (!"REQUEST".equals(direction) && !"RESPONSE".equals(direction)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Header direction must be REQUEST or RESPONSE");
            }
            if (!"ADD".equals(action) && !"REMOVE".equals(action)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Header action must be ADD or REMOVE");
            }
            if ("ADD".equals(action) &&
                    (rule.getHeaderValue() == null || rule.getHeaderValue().trim().isEmpty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ADD header rules require headerValue");
            }
        }
    }

    private static final Set<String> SUPPORTED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private Set<String> normalizeMethods(Set<String> methods) {
        Set<String> normalized = new HashSet<>();
        for (String method : methods) {
            String value = method.toUpperCase(Locale.ROOT);
            if (!SUPPORTED_METHODS.contains(value)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported HTTP method: " + method);
            }
            normalized.add(value);
        }
        return normalized;
    }

    private void saveVersion(ApiRoute route, String changeType) {
        RouteConfigurationVersion version = new RouteConfigurationVersion();
        version.setOrganization(route.getOrganization());
        version.setRouteId(route.getRouteId());
        version.setVersionNumber(route.getConfigurationVersion());
        version.setChangeType(changeType);
        version.setConfigurationSnapshot(objectMapper.writeValueAsString(RouteResponse.from(route)));
        version.setCreatedAt(LocalDateTime.now());

        versionRepository.save(version);

        // Publish a Spring application event within the current DB transaction.
        // GatewayConfigurationKafkaPublisher sends it to Kafka AFTER_COMMIT.
        gatewayConfigurationEventService.publish(
                changeType,
                route.getOrganization().getId(),
                route.getRouteId(),
                route.getConfigurationVersion()
        );
    }

    private List<RouteResponse> toResponses(List<ApiRoute> routes) {
        List<RouteResponse> response = new ArrayList<>();
        for (ApiRoute route : routes) {
            response.add(RouteResponse.from(route));
        }
        return response;
    }

    private ApiRoute getOwnedRoute(Long id, Long organizationId) {
        return apiRouteRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Route not found"));
    }

    // GATEWAY METHOD
    @Transactional(readOnly = true)
    public List<AuditRecordResponse> getAuditRecords() {
        Organization organization = tenantContext.getOrganization();

        List<AuditRecordResponse> response = new ArrayList<>();
        List<ControlPlaneAuditRecord> records = auditRecordRepository
                .findAllByOrganizationIdOrderByCreatedAtDesc(organization.getId());

        for (ControlPlaneAuditRecord record : records) {
            response.add(AuditRecordResponse.from(record));
        }
        return response;
    }
}
