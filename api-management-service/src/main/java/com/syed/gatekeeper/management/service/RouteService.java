package com.syed.gatekeeper.management.service;

import com.syed.gatekeeper.management.dto.requestDto.RouteRequest;
import com.syed.gatekeeper.management.dto.requestDto.RouteStatusRequest;
import com.syed.gatekeeper.management.dto.responseDto.AuditRecordResponse;
import com.syed.gatekeeper.management.dto.responseDto.RouteResponse;
import com.syed.gatekeeper.management.dto.responseDto.RouteVersionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteService {
    public RouteResponse create(@Valid RouteRequest request) {
    }

    public List<RouteResponse> findAllForTenant() {
    }

    public RouteResponse findById(Long id) {
    }

    public RouteResponse update(Long id, @Valid RouteRequest request) {
    }

    public RouteResponse changeStatus(Long id, @Valid RouteStatusRequest request) {
    }

    public void delete(Long id) {
    }

    public List<RouteVersionResponse> getVersions(String routeId) {
    }

    public List<AuditRecordResponse> getAuditRecords() {
        return null;
    }

    public List<RouteResponse> findAllActiveForGateway() {
    }
}
