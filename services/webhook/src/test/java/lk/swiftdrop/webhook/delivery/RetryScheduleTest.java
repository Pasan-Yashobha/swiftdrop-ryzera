package lk.swiftdrop.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lk.swiftdrop.webhook.testsupport.TestProperties;
import org.junit.jupiter.api.Test;

class RetryScheduleTest {

    private final RetrySchedule schedule = new RetrySchedule(TestProperties.defaults());

    @Test
    void sixAttemptsWithGrowingDelays() {
        assertThat(schedule.maxAttempts()).isEqualTo(6);
        assertThat(schedule.after(1)).contains(new RetrySchedule.Delay("webhook.delay.5s", Duration.ofSeconds(5)));
        assertThat(schedule.after(2)).contains(new RetrySchedule.Delay("webhook.delay.30s", Duration.ofSeconds(30)));
        assertThat(schedule.after(3)).contains(new RetrySchedule.Delay("webhook.delay.2min", Duration.ofMinutes(2)));
        assertThat(schedule.after(4)).contains(new RetrySchedule.Delay("webhook.delay.10min", Duration.ofMinutes(10)));
        assertThat(schedule.after(5)).contains(new RetrySchedule.Delay("webhook.delay.30min", Duration.ofMinutes(30)));
    }

    @Test
    void thereIsNoRetryAfterTheSixthAttempt() {
        assertThat(schedule.after(6)).isEmpty();
        assertThat(schedule.after(0)).isEmpty();
    }
}
