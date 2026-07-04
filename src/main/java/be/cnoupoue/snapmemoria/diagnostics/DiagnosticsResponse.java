package be.cnoupoue.snapmemoria.diagnostics;

import be.cnoupoue.snapmemoria.platform.PlatformDiagnosticInfo;

public record DiagnosticsResponse(
    String appVersion,
    PlatformDiagnosticInfo platform,
    VideoPreviewDiagnosticsResponse videoPreviews,
    SourceDiagnosticsResponse sources,
    DatabaseDiagnosticsResponse database) {}
