package be.cnoupoue.snapmemoria.web;

public record ApiErrorResponse(int status, String code, String message, String timestamp) {}
