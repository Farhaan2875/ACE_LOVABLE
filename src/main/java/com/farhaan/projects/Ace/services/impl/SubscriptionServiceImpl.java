package com.farhaan.projects.Ace.services.impl;

import com.farhaan.projects.Ace.Dto.billing.CheckoutRequest;
import com.farhaan.projects.Ace.Dto.billing.CheckoutResponse;
import com.farhaan.projects.Ace.Dto.billing.PortalResponse;
import com.farhaan.projects.Ace.Dto.billing.SubscriptionResponse;
import com.farhaan.projects.Ace.billing.StripeClient;
import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.entities.Plan;
import com.farhaan.projects.Ace.entities.Subscription;
import com.farhaan.projects.Ace.entities.User;
import com.farhaan.projects.Ace.enums.SubscriptionStatus;
import com.farhaan.projects.Ace.error.BadRequestException;
import com.farhaan.projects.Ace.error.ResourceNotFoundException;
import com.farhaan.projects.Ace.mapper.PlanMapper;
import com.farhaan.projects.Ace.repositories.PlanRepository;
import com.farhaan.projects.Ace.repositories.SubscriptionRepository;
import com.farhaan.projects.Ace.repositories.UserLogRepository;
import com.farhaan.projects.Ace.repositories.UserRepository;
import com.farhaan.projects.Ace.services.PlanService;
import com.farhaan.projects.Ace.services.SubscriptionService;
import com.farhaan.projects.Ace.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PlanService planService;
    private final PlanMapper planMapper;
    private final UserRepository userRepository;
    private final UserLogRepository userLogRepository;
    private final StripeClient stripe;
    private final AceProperties props;

    // ------------------------------------------------------------------ read

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getCurrentSubscription(Long userId) {
        Optional<Subscription> sub = subscriptionRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, PlanServiceImpl.PAID_STATUSES);

        Instant since = sub.map(Subscription::getCurrentPeriodStart).orElse(Instant.now().minus(Duration.ofDays(30)));
        long used = userLogRepository.sumTokensSince(userId, since);

        if (sub.isPresent()) {
            Subscription s = sub.get();
            return new SubscriptionResponse(planMapper.toPlanResponse(s.getPlan()), s.getStatus().name(), s.getCurrentPeriodEnd(), used);
        }
        // No paid subscription = FREE plan
        return new SubscriptionResponse(planMapper.toPlanResponse(planService.getFreePlan()), SubscriptionStatus.ACTIVE.name(), null, used);
    }

    // ------------------------------------------------------------------ checkout / portal

    @Override
    @Transactional
    public CheckoutResponse createCheckoutSessionUrl(CheckoutRequest request, Long userId) {
        if (request.planId() == null) throw new BadRequestException("planId is required");
        Plan plan = planRepository.findById(request.planId())
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Plan", String.valueOf(request.planId())));
        if ("FREE".equals(plan.getName())) throw new BadRequestException("The free plan needs no checkout");

        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        // Dev shortcut: no Stripe account needed, the plan is activated immediately.
        if (!stripe.isConfigured()) {
            if (!props.getBilling().isDevMode()) throw new BadRequestException("Billing is not configured on this server");
            activateDevSubscription(user, plan);
            return new CheckoutResponse(props.getFrontendUrl() + "/billing?status=success");
        }
        if (plan.getStripePriceId() == null || plan.getStripePriceId().isBlank()) {
            throw new BadRequestException("This plan has no Stripe price configured");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", "subscription");
        form.put("line_items[0][price]", plan.getStripePriceId());
        form.put("line_items[0][quantity]", "1");
        form.put("success_url", props.getFrontendUrl() + "/billing?status=success");
        form.put("cancel_url", props.getFrontendUrl() + "/billing?status=cancelled");
        form.put("client_reference_id", userId.toString());
        form.put("metadata[planId]", plan.getId().toString());
        Optional<Subscription> existing = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (existing.isPresent() && existing.get().getStripeCustomerId() != null) {
            form.put("customer", existing.get().getStripeCustomerId());
        } else {
            form.put("customer_email", user.getUsername());
        }
        JsonNode session = stripe.post("checkout/sessions", form);
        return new CheckoutResponse(session.path("url").asText());
    }

    @Override
    @Transactional
    public PortalResponse openCustomerPortal(Long userId) {
        Subscription sub = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new BadRequestException("You have no billing account yet"));

        if (sub.getStripeSubscriptionId() != null && sub.getStripeSubscriptionId().startsWith("dev-")) {
            sub.setStatus(SubscriptionStatus.CANCELED);        // dev mode: the "portal" simply cancels the plan
            subscriptionRepository.save(sub);
            return new PortalResponse(props.getFrontendUrl() + "/billing");
        }
        if (!stripe.isConfigured() || sub.getStripeCustomerId() == null) {
            throw new BadRequestException("Billing portal is not available");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("customer", sub.getStripeCustomerId());
        form.put("return_url", props.getFrontendUrl() + "/billing");
        return new PortalResponse(stripe.post("billing_portal/sessions", form).path("url").asText());
    }

    private void activateDevSubscription(User user, Plan plan) {
        Instant now = Instant.now();
        subscriptionRepository.save(Subscription.builder()
                .user(user).plan(plan).status(SubscriptionStatus.ACTIVE)
                .stripeCustomerId("dev-customer")
                .stripeSubscriptionId("dev-" + UUID.randomUUID())
                .currentPeriodStart(now).currentPeriodEnd(now.plus(Duration.ofDays(30)))
                .build());
    }

    // ------------------------------------------------------------------ webhook

    @Override
    @Transactional
    public void handleStripeWebhook(String payload, String signatureHeader) {
        if (!stripe.verifySignature(payload, signatureHeader)) {
            throw new BadRequestException("Invalid Stripe signature");
        }
        JsonNode event = Json.read(payload);
        JsonNode object = event.path("data").path("object");
        String type = event.path("type").asText();
        log.info("Stripe webhook: {}", type);

        switch (type) {
            case "checkout.session.completed" -> onCheckoutCompleted(object);
            case "customer.subscription.created", "customer.subscription.updated" -> onSubscriptionChanged(object);
            case "customer.subscription.deleted" -> subscriptionRepository.findByStripeSubscriptionId(object.path("id").asText())
                    .ifPresent(s -> { s.setStatus(SubscriptionStatus.CANCELED); subscriptionRepository.save(s); });
            default -> { /* we don't care about other events */ }
        }
    }

    private void onCheckoutCompleted(JsonNode session) {
        Long userId = parseLong(session.path("client_reference_id").asText(null));
        Long planId = parseLong(session.path("metadata").path("planId").asText(null));
        String subscriptionId = session.path("subscription").asText(null);
        if (userId == null || planId == null || subscriptionId == null) {
            log.warn("checkout.session.completed without userId/planId/subscription - ignored");
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        Plan plan = planRepository.findById(planId).orElse(null);
        if (user == null || plan == null) return;

        Subscription sub = subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
                .orElseGet(() -> Subscription.builder().stripeSubscriptionId(subscriptionId).build());
        sub.setUser(user);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStripeCustomerId(session.path("customer").asText(null));
        if (sub.getCurrentPeriodStart() == null) sub.setCurrentPeriodStart(Instant.now());
        subscriptionRepository.save(sub);
    }

    private void onSubscriptionChanged(JsonNode stripeSub) {
        Subscription sub = subscriptionRepository.findByStripeSubscriptionId(stripeSub.path("id").asText()).orElse(null);
        if (sub == null) return;   // the checkout.session.completed event creates the row

        sub.setStatus(mapStatus(stripeSub.path("status").asText()));
        sub.setCancelAtPeriodEnd(stripeSub.path("cancel_at_period_end").asBoolean(false));

        // Newer Stripe API versions moved the period to the subscription item
        JsonNode item = stripeSub.path("items").path("data").path(0);
        Instant start = epoch(stripeSub.path("current_period_start"), item.path("current_period_start"));
        Instant end = epoch(stripeSub.path("current_period_end"), item.path("current_period_end"));
        if (start != null) sub.setCurrentPeriodStart(start);
        if (end != null) sub.setCurrentPeriodEnd(end);

        String priceId = item.path("price").path("id").asText(null);
        if (priceId != null) planRepository.findByStripePriceId(priceId).ifPresent(sub::setPlan);   // plan upgrade/downgrade
        subscriptionRepository.save(sub);
    }

    private static SubscriptionStatus mapStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            default -> SubscriptionStatus.INCOMPLETE;
        };
    }

    private static Instant epoch(JsonNode primary, JsonNode fallback) {
        JsonNode node = primary.isNumber() ? primary : fallback;
        return node.isNumber() ? Instant.ofEpochSecond(node.asLong()) : null;
    }

    private static Long parseLong(String value) {
        try { return value == null ? null : Long.parseLong(value); } catch (NumberFormatException e) { return null; }
    }
}
