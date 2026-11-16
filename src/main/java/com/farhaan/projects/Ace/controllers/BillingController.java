package com.farhaan.projects.Ace.controllers;


import com.farhaan.projects.Ace.Dto.billing.*;
import com.farhaan.projects.Ace.security.CurrentUser;
import com.farhaan.projects.Ace.services.PlanService;
import com.farhaan.projects.Ace.services.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BillingController {
    private final PlanService planService;
    private final SubscriptionService subscriptionService;


    @GetMapping("/api/plans")   // public: the pricing page works before login
    public ResponseEntity<List<PlanResponse>> getAllPlans() {
        return ResponseEntity.ok(planService.getAllActivePlans());
    }

    @GetMapping("/api/me/subscription")
    public ResponseEntity<SubscriptionResponse> getMySubscription() {
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription(CurrentUser.id()));
    }

    @PostMapping("/api/stripe/checkout")
    public ResponseEntity<CheckoutResponse> createCheckoutReponse(
            @RequestBody CheckoutRequest request
    ) {
        return ResponseEntity.ok(subscriptionService.createCheckoutSessionUrl(request, CurrentUser.id()));
    }

    @PostMapping("/api/stripe/portal")
    public ResponseEntity<PortalResponse> getCustomerPortal() {
        return ResponseEntity.ok(subscriptionService.openCustomerPortal(CurrentUser.id()));
    }

    /** Stripe -> us. No login; the request is authenticated by Stripe's signature header. Needs the RAW body. */
    @PostMapping("/api/stripe/webhook")
    public ResponseEntity<Void> stripeWebhook(@RequestBody String payload,
                                              @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        subscriptionService.handleStripeWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

}
