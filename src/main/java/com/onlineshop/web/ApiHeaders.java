package com.onlineshop.web;

public final class ApiHeaders {

    // Set by the API gateway from the validated JWT. Never accepted directly from the internet.
    public static final String USER_ID = "X-User-Id";

    private ApiHeaders() {
    }
}
