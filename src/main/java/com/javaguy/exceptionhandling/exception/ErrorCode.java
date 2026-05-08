package com.javaguy.exceptionhandling.exception;

/**
 * Central registry of machine-readable error codes included in every error response.
 *
 * <p>Codes follow the pattern {@code DOMAIN_CONDITION} so that monitoring dashboards,
 * log queries, and client-side error handling can key on a stable string rather than
 * HTTP status codes, which are too coarse-grained.
 *
 * <p>Each code maps to exactly one error scenario, making it safe to use them as
 * alert-routing keys in observability platforms.
 */
public final class ErrorCode {

    public static final String RESOURCE_NOT_FOUND         = "RESOURCE_NOT_FOUND";
    public static final String RESOURCE_GONE              = "RESOURCE_GONE";
    public static final String DATA_CONFLICT              = "DATA_CONFLICT";
    public static final String FORBIDDEN                  = "FORBIDDEN";
    public static final String EXTERNAL_SERVICE_ERROR     = "EXTERNAL_SERVICE_ERROR";
    public static final String RESPONSE_STATUS_ERROR      = "RESPONSE_STATUS_ERROR";
    public static final String INTERNAL_ERROR             = "INTERNAL_ERROR";

    public static final String USER_EMAIL_CONFLICT        = "USER_EMAIL_CONFLICT";
    public static final String USER_INACTIVE              = "USER_INACTIVE";

    public static final String PRODUCT_SKU_CONFLICT       = "PRODUCT_SKU_CONFLICT";
    public static final String PRODUCT_INSUFFICIENT_STOCK = "PRODUCT_INSUFFICIENT_STOCK";

    public static final String ORDER_INVALID_CANCEL       = "ORDER_INVALID_CANCEL";

    private ErrorCode() {}
}
