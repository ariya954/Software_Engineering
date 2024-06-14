package ir.ramtung.tinyme.domain.service.MatchingControls;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Trade;
import org.springframework.stereotype.Component;

@Component
public class PositionControl implements MatchingControl {
    public void matchingAccepted(Order order, MatchResult matchResult) {
        if (!matchResult.trades().isEmpty()) {
            for (Trade trade : matchResult.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
    }
}