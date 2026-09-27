package com.aux_app.dto.users;

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
