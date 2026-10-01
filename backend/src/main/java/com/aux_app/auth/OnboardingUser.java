package com.aux_app.auth;

import io.swagger.v3.oas.annotations.Parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Like @CurrentUser but also lets in accounts still onboarding. Only for onboarding endpoints
@Parameter(hidden = true) // injected from the token, not part of the request
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnboardingUser {}

