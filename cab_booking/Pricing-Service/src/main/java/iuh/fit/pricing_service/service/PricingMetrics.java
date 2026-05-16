package iuh.fit.pricing_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PricingMetrics {

    private final MeterRegistry meterRegistry;

    public PricingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordEstimateRequest() {
        Counter.builder("pricing_estimate_requests_total")
                .description("Total fare estimate requests")
                .register(meterRegistry)
                .increment();
    }

    public void recordExternalApiLatency(String provider, Duration duration) {
        Timer.builder("pricing_external_api_latency")
                .description("External API latency")
                .tag("provider", provider)
                .register(meterRegistry)
                .record(duration);
    }

    public void recordFallback(String source) {
        Counter.builder("pricing_estimate_fallback_total")
                .description("Total pricing fallback executions")
                .tag("source", source)
                .register(meterRegistry)
                .increment();
    }

    public void recordSurgeApplied() {
        Counter.builder("pricing_surge_applied_total")
                .description("Total estimates with surge multiplier greater than 1.0")
                .register(meterRegistry)
                .increment();
    }

    public void recordConfirmFailure(String reason) {
        Counter.builder("pricing_quote_confirm_failed_total")
                .description("Total quote confirmation failures")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }
}
