package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.MatchingControls.MatchingControlsList;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.ListIterator;

@Service
public class Matcher {
    private MatchingControlsList matchingControls = new MatchingControlsList();

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

            MatchingOutcome outcome = matchingControls.canAcceptTrade(order, trade);
            if(outcome != MatchingOutcome.EXECUTED) {
                rollbackTrades(order, trades);
                return new MatchResult(outcome, null, new LinkedList<>());
            }

            matchingControls.tradeAccepted(order, trade);
            trades.add(trade);
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

    public MatchResult execute(Order order, MatchingState matchingState, int openingPrice) {
        MatchResult matchResult = match(order, matchingState, openingPrice);
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return matchResult;

        MatchingOutcome outcome = matchingControls.canAcceptMatching(order, matchResult);
        if(outcome != MatchingOutcome.EXECUTED) {
            rollbackTrades(order, matchResult.trades());
            return new MatchResult(outcome, null, new LinkedList<>());
        }

        matchingControls.matchingAccepted(order, matchResult);

        return matchResult;
    }
}