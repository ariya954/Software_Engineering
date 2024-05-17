package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.ListIterator;

@Service
public class Matcher {
    public MatchResult match(Order order, MatchingState matchingState, int openingPrice) {
        OrderBook orderBook = order.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();

        while (orderBook.hasOrderOfType(order.getSide().opposite()) && order.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(order);
            if (matchingOrder == null || (matchingState == MatchingState.AUCTION && matchingOrder.getPrice() > openingPrice))
                break;

            Trade trade;
            if(matchingState == MatchingState.AUCTION)
                trade = new Trade(order.getSecurity(), openingPrice, Math.min(order.getQuantity(), matchingOrder.getQuantity()), order, matchingOrder);
            else
                trade = new Trade(order.getSecurity(), matchingOrder.getPrice(), Math.min(order.getQuantity(), matchingOrder.getQuantity()), order, matchingOrder);
            if (order.getSide() == Side.BUY) {
                if(matchingState == MatchingState.AUCTION)
                    order.getBroker().increaseCreditBy( (order.getPrice() - trade.getPrice()) * trade.getQuantity());
                else {
                    if (trade.buyerHasEnoughCredit())
                        trade.decreaseBuyersCredit();
                    else {
                        rollbackTrades(order, trades);
                        return MatchResult.notEnoughCredit();
                    }
                }
            }
            trade.increaseSellersCredit();
            trades.add(trade);

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
        return MatchResult.executed(order, trades);
    }

    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        assert newOrder.getSide() == Side.BUY;
        newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
        trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));

        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreSellOrder(it.previous().getSell());
        }
    }

    public MatchResult execute(Order order, int minimumExecutionQuantity, MatchingState matchingState, int openingPrice) {
        int order_quantity = order.getQuantity();
        MatchResult result = match(order, matchingState, openingPrice);
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return result;

        int executed_quantity = order_quantity - result.remainder().getQuantity();
        if(executed_quantity < minimumExecutionQuantity){
            rollbackTrades(order, result.trades());
            return MatchResult.notEnoughExecutedQuantity();
        }

        if (matchingState != MatchingState.AUCTION && result.remainder().getQuantity() > 0) {
            if (order.getSide() == Side.BUY) {
                if (!order.getBroker().hasEnoughCredit(order.getValue())) {
                    rollbackTrades(order, result.trades());
                    return MatchResult.notEnoughCredit();
                }
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }
        else if(matchingState == MatchingState.AUCTION && result.remainder().getQuantity() == 0)
            order.getSecurity().getOrderBook().removeByOrderId(order.getSide(), order.getOrderId());

        if (!result.trades().isEmpty()) {
            for (Trade trade : result.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
        return result;
    }

}
