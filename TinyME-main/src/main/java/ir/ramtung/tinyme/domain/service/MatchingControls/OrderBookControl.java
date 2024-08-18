package ir.ramtung.tinyme.domain.service.MatchingControls;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.springframework.stereotype.Component;

@Component
public class OrderBookControl implements MatchingControl {
    public void matchingAccepted(Order order, MatchResult matchResult) {
        if (order.getSecurity().getMatchingState() == MatchingState.CONTINUOUS && matchResult.remainder().getQuantity() > 0)
            order.getSecurity().getOrderBook().enqueue(matchResult.remainder());
        else if(order.getSecurity().getMatchingState() == MatchingState.AUCTION && matchResult.remainder().getQuantity() == 0)
            order.getSecurity().getOrderBook().removeByOrderId(order.getSide(), order.getOrderId());
    }
    public void tradeAccepted(Order order, Trade trade) {
        OrderBook orderBook = order.getSecurity().getOrderBook();
        Order matchingOrder = orderBook.matchWithFirst(order);
        if (order.getQuantity() >= matchingOrder.getQuantity()) {
            order.decreaseQuantity(matchingOrder.getQuantity());
            orderBook.removeFirst(matchingOrder.getSide());
            if (matchingOrder instanceof IcebergOrder icebergOrder) {
                icebergOrder.decreaseQuantity(matchingOrder.getQuantity());
                icebergOrder.replenish();
                if (icebergOrder.getQuantity() > 0)
                    orderBook.enqueue(icebergOrder);
            }
        } else {
            matchingOrder.decreaseQuantity(order.getQuantity());
            order.makeQuantityZero();
        }
    }
}