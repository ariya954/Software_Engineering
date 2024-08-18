package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private MatchingState matchingState = MatchingState.CONTINUOUS;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private int lastTradedPrice = 0;
    @Builder.Default
    private int openingPrice = 0;
    @Builder.Default
    private int tradableQuantity = 0;

    @Builder.Default
    private OrderBook orderBook = new OrderBook();

    public void setMatchingState(MatchingState targetState) {
        matchingState = targetState;
    }

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        Order order;
        if (enterOrderRq.getPeakSize() == 0) {
            if (enterOrderRq.getMinimumExecutionQuantity() == 0) {
                order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(), OrderStatus.NEW);
                if(matchingState == MatchingState.CONTINUOUS && enterOrderRq.getStopPrice() > 0 && enterOrderRq.getSide() == Side.BUY)
                    broker.increaseCreditBy(order.getValue());
            }
            else
                order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(), OrderStatus.NEW, enterOrderRq.getMinimumExecutionQuantity());
        }
        else {
            if (enterOrderRq.getMinimumExecutionQuantity() == 0)
                order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize());
            else
                order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), enterOrderRq.getMinimumExecutionQuantity());
        }

        if (matchingState == MatchingState.AUCTION)
            return auctionMatching(order);

        return continuousMatching(order, matcher);
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        if(matchingState == MatchingState.AUCTION) {
            openingPrice = orderBook.calculateOpeningPriceAccordingTo(lastTradedPrice);
            tradableQuantity = orderBook.calculateTradableQuantityAccordingTo(openingPrice);
        }
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);

        boolean losesPriority = originalOrder.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != originalOrder.getPrice()
                || ((originalOrder instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));
        if (!losesPriority) {
            if (updateOrderRq.getSide() == Side.BUY)
                originalOrder.getBroker().decreaseCreditBy((updateOrderRq.getQuantity() - originalOrder.getQuantity()) * originalOrder.getPrice());
            return MatchResult.executed(null, List.of());
        }

        orderBook.removeByOrderId(originalOrder.getSide(), originalOrder.getOrderId());
        if (updateOrderRq.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(originalOrder.getValue());

        order.markAsNew();
        if (matchingState == MatchingState.CONTINUOUS) {
            MatchResult matchResult = continuousMatching(order, matcher);
            if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
                orderBook.enqueue(originalOrder);
                if (updateOrderRq.getSide() == Side.BUY)
                    originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
            return matchResult;
        }
        MatchResult matchResult = auctionMatching(order);
        if (updateOrderRq.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
        return matchResult;
    }

    public List<Trade> reopening(Matcher matcher) {
        List<Trade> trades = new ArrayList<>();

        for(Order buyOrder : orderBook.findTradableBuyOrdersAccordingTo(openingPrice)) {
            if (orderBook.findTradableSellOrdersAccordingTo(openingPrice).size() == 0)
                break;
            MatchResult matchResult = continuousMatching(buyOrder, matcher);
            for (Trade trade : matchResult.trades())
                trades.add(trade);
        }

        return trades;
    }

    private MatchResult continuousMatching(Order order, Matcher matcher) {
        MatchResult matchResult = matcher.execute(order, matchingState, openingPrice);
        if(!matchResult.trades().isEmpty())
            lastTradedPrice = matchResult.trades().getLast().getPrice();
        return matchResult;
    }

    private MatchResult auctionMatching(Order order) {
        orderBook.enqueue(order);
        openingPrice = orderBook.calculateOpeningPriceAccordingTo(lastTradedPrice);
        tradableQuantity = orderBook.calculateTradableQuantityAccordingTo(openingPrice);
        return null;
    }
}