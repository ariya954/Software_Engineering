package ir.ramtung.tinyme.domain.service.MatchingControls;

import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Side;
import ir.ramtung.tinyme.domain.entity.Trade;
import org.springframework.stereotype.Component;

@Component
public class CreditControl implements MatchingControl {
    public boolean canAcceptTrade(Order order, Trade trade) {
        if(order.getSide() == Side.BUY && !trade.buyerHasEnoughCredit())
            return false;
        return true;
    }
    public void tradeAccepted(Order order, Trade trade) {
        if (order.getSide() == Side.BUY) {
            if(order.getSecurity().getMatchingState() == MatchingState.AUCTION)
                order.getBroker().increaseCreditBy( (order.getPrice() - trade.getPrice()) * trade.getQuantity());
            else
                trade.decreaseBuyersCredit();
        }
        trade.increaseSellersCredit();
    }
    public boolean canAcceptMatching(Order order, MatchResult matchResult) {
        if (order.getSecurity().getMatchingState() == MatchingState.CONTINUOUS && matchResult.remainder().getQuantity() > 0)
            if (order.getSide() == Side.BUY)
                if (!order.getBroker().hasEnoughCredit(order.getValue()))
                    return false;
        return true;
    }
    public void matchingAccepted(Order order, MatchResult matchResult) {
        if (order.getSecurity().getMatchingState() == MatchingState.CONTINUOUS && matchResult.remainder().getQuantity() > 0)
            if (order.getSide() == Side.BUY)
                order.getBroker().decreaseCreditBy(order.getValue());
    }
}