package ir.ramtung.tinyme.domain.service.MatchingControls;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;

import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Trade;
import org.springframework.stereotype.Component;
@Component
public class MatchingControlsList {
    private CreditControl creditControl = new CreditControl();
    private PositionControl positionControl = new PositionControl();
    private OrderBookControl orderBookControl = new OrderBookControl();
    private MinimumExecutionQuantityControl minimumExecutionQuantityControl = new MinimumExecutionQuantityControl();

    public MatchingOutcome canAcceptTrade(Order order, Trade trade) {
        if(!creditControl.canAcceptTrade(order, trade))
            return MatchingOutcome.NOT_ENOUGH_CREDIT;
        return MatchingOutcome.EXECUTED;
    }

    public void tradeAccepted(Order order, Trade trade) {
        creditControl.tradeAccepted(order, trade);
        orderBookControl.tradeAccepted(order, trade);
    }

    public MatchingOutcome canAcceptMatching(Order order, MatchResult matchResult) {
        if(!minimumExecutionQuantityControl.canAcceptMatching(order, matchResult))
            return MatchingOutcome.NOT_ENOUGH_EXECUTED_QUANTITY;
        if(!creditControl.canAcceptMatching(order, matchResult))
            return MatchingOutcome.NOT_ENOUGH_CREDIT;
        return MatchingOutcome.EXECUTED;
    }

    public void matchingAccepted(Order order, MatchResult matchResult) {
        creditControl.matchingAccepted(order, matchResult);
        orderBookControl.matchingAccepted(order, matchResult);
        positionControl.matchingAccepted(order, matchResult);
    }
}