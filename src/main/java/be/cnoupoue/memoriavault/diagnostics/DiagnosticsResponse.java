package be.cnoupoue.memoriavault.diagnostics;

import be.cnoupoue.memoriavault.platform.PlatformDiagnosticInfo;

public record DiagnosticsResponse(
    String appVersion,
    PlatformDiagnosticInfo platform,
    VideoPreviewDiagnosticsResponse videoPreviews,
    SourceDiagnosticsResponse sources,
    DatabaseDiagnosticsResponse database) {}
