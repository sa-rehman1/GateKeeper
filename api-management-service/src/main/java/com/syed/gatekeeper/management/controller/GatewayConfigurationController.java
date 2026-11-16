package com.syed.gatekeeper.management.controller;

import com.syed.gatekeeper.management.dto.responseDto.RouteResponse;
import com.syed.gatekeeper.management.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gateway/routes")
@RequiredArgsConstructor
public class GatewayConfigurationController {

    private final RouteService routeService;



    @GetMapping
    public ResponseEntity<List<RouteResponse>> getActiveRoutes() {
        return ResponseEntity.ok(routeService.findAllActiveForGateway());
    }
}