package com.vspicy.video.dto;

import java.util.List;

public record SchemaDiagnosticTableView(
        String tableName,
        Boolean exists,
        Long rowCount,
        List<SchemaDiagnosticColumnView> columns,
        List<String> missingColumns
) {
}
