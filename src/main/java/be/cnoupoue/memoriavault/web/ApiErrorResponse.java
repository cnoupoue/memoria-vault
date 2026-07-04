package be.cnoupoue.memoriavault.web;

public record ApiErrorResponse(int status, String code, String message, String timestamp) {}
