package com.vspicy.video.dto;

import java.util.List;

public record SchemaDiagnosticView(
        String databaseName,
        Integer tableCount,
        Integer missingTableCount,
        Integer missingColumnCount,
        List<SchemaDiagnosticTableView> tables
) {
}
