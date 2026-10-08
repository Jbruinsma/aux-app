package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Steps of onboarding in order. DONE means the account is ready to use")
public enum OnboardingStep {
    USERNAME(1),
    PFP(2),
    DONE(3);

    private final int order;

    OnboardingStep(int order) { this.order = order; }

    public int getOrder() { return order; }

    public OnboardingStep next() {
        for (OnboardingStep s : values()) {
            if (s.order == order + 1) return s;
        }
        return null;
    }
}
