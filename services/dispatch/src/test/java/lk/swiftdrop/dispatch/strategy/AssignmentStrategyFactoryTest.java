package lk.swiftdrop.dispatch.strategy;

import java.util.List;
import lk.swiftdrop.dispatch.common.BadRequestException;
import lk.swiftdrop.dispatch.domain.StrategyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class AssignmentStrategyFactoryTest {

    @Test
    @DisplayName("Returns correct strategy bean by StrategyType")
    void returnsCorrectStrategy() {
        AssignmentStrategy leastLoaded = Mockito.mock(AssignmentStrategy.class);
        when(leastLoaded.type()).thenReturn(StrategyType.LEAST_LOADED);

        AssignmentStrategy zoneBased = Mockito.mock(AssignmentStrategy.class);
        when(zoneBased.type()).thenReturn(StrategyType.ZONE_BASED);

        AssignmentStrategyFactory factory = new AssignmentStrategyFactory(List.of(leastLoaded, zoneBased));

        assertThat(factory.getStrategy(StrategyType.LEAST_LOADED)).isSameAs(leastLoaded);
        assertThat(factory.getStrategy(StrategyType.ZONE_BASED)).isSameAs(zoneBased);
    }

    @Test
    @DisplayName("Throws BadRequestException when strategy is null or unregistered")
    void throwsOnMissingStrategy() {
        AssignmentStrategyFactory factory = new AssignmentStrategyFactory(List.of());

        assertThatThrownBy(() -> factory.getStrategy(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be specified");

        assertThatThrownBy(() -> factory.getStrategy(StrategyType.LEAST_LOADED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No assignment strategy found");
    }
}

