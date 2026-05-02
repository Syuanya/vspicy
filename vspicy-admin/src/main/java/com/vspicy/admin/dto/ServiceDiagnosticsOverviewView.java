package com.vspicy.admin.dto;

import java.util.List;

public record ServiceDiagnosticsOverviewView(
        Long collectedAt,
        Integer serviceTotal,
        Integer serviceUp,
        Integer serviceDown,
        Integer middlewareTotal,
        Integer middlewareUp,
        Integer middlewareDown,
        List<ServiceProbeView> services,
        List<ServiceProbeView> middlewares,
        List<String> suggestions,
        List<String> powershellChecks
) {
}
