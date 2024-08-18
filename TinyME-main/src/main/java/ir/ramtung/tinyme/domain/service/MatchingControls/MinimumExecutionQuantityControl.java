package ir.ramtung.tinyme.domain.service.MatchingControls;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Trade;
import org.springframework.stereotype.Component;

@Component
public class MinimumExecutionQuantityControl implements MatchingControl {
    public boolean canAcceptMatching(Order order, MatchResult matchResult) {
        int executed_quantity = matchResult.trades().stream().mapToInt(Trade::getQuantity).sum();
        if (executed_quantity < order.getMinimumExecutionQuantity())
            return false;
        return true;
    }
}