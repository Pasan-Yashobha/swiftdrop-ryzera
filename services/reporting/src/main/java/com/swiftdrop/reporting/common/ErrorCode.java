package com.swiftdrop.reporting.common;

/**
 * Canonical error codes for the reporting service.
 * Matches the team's shared error format so the API Gateway
 * and other services can handle errors uniformly.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    RESOURCE_NOT_FOUND,
    FORBIDDEN,
    UNAUTHORIZED,
    INVALID_DATE_RANGE,
    DATE_RANGE_TOO_LARGE,
    INTERNAL_ERROR
}
