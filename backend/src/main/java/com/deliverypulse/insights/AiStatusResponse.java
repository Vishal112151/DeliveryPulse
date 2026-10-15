package com.deliverypulse.insights;

/** Lets the frontend label the insights panel without exposing any credential. */
public record AiStatusResponse(
        boolean enabled,
        boolean configured,
        boolean externalDataSharingAuthorized,
        boolean available,
        String provider,
        String model,
        InsightStatus status,
        String message) {
}
