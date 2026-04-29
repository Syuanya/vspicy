package com.vspicy.video.dto;

public record SchemaDiagnosticColumnView(
        String tableName,
        String columnName,
        Boolean exists,
        String dataType,
        String columnType,
        String nullable
) {
}
