package lk.swiftdrop.dispatch.strategy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lk.swiftdrop.dispatch.common.BadRequestException;
import lk.swiftdrop.dispatch.domain.StrategyType;
import org.springframework.stereotype.Component;

/**
 * Registry and factory for assignment strategies.
 * Populated automatically at application startup from all AssignmentStrategy beans.
 */
@Component
public class AssignmentStrategyFactory {

    private final Map<StrategyType, AssignmentStrategy> strategies = new EnumMap<>(StrategyType.class);

    public AssignmentStrategyFactory(List<AssignmentStrategy> strategyList) {
        for (AssignmentStrategy strategy : strategyList) {
            strategies.put(strategy.type(), strategy);
        }
    }

    public AssignmentStrategy getStrategy(StrategyType type) {
        if (type == null) {
            throw new BadRequestException("Assignment strategy type must be specified");
        }
        AssignmentStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new BadRequestException("No assignment strategy found for type: " + type);
        }
        return strategy;
    }
}

