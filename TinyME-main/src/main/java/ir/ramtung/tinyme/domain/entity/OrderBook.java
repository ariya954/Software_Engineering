package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import static java.lang.Math.*;

@Getter
public class OrderBook {
    private final LinkedList<Order> buyQueue;
    private final LinkedList<Order> sellQueue;

    public OrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
    }

    public void enqueue(Order order) {
        List<Order> queue = getQueue(order.getSide());
        ListIterator<Order> it = queue.listIterator();
        while (it.hasNext()) {
            if (order.queuesBefore(it.next())) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
    }

    private LinkedList<Order> getQueue(Side side) {
        return side == Side.BUY ? buyQueue : sellQueue;
    }

    public Order findByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        for (Order order : queue) {
            if (order.getOrderId() == orderId)
                return order;
        }
        return null;
    }

    public boolean removeByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        var it = queue.listIterator();
        while (it.hasNext()) {
            if (it.next().getOrderId() == orderId) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public Order matchWithFirst(Order newOrder) {
        var queue = getQueue(newOrder.getSide().opposite());
        if (newOrder.matches(queue.getFirst()))
            return queue.getFirst();
        else
            return null;
    }

    public void putBack(Order order) {
        LinkedList<Order> queue = getQueue(order.getSide());
        order.queue();
        queue.addFirst(order);
    }

    public void restoreSellOrder(Order sellOrder) {
        removeByOrderId(Side.SELL, sellOrder.getOrderId());
        putBack(sellOrder);
    }

    public boolean hasOrderOfType(Side side) {
        return !getQueue(side).isEmpty();
    }

    public void removeFirst(Side side) {
        getQueue(side).removeFirst();
    }

    public int totalSellQuantityByShareholder(Shareholder shareholder) {
        return sellQueue.stream()
                .filter(order -> order.getShareholder().equals(shareholder))
                .mapToInt(Order::getTotalQuantity)
                .sum();
    }

    public int calculateOpeningPriceAccordingTo(int lastTradedPrice) {
        if (buyQueue.size() == 0 || sellQueue.size() == 0)
            return 0;

        int openingPrice = 0;
        int maximumTradableQuantity = 0;

        for (int currentOpeningPrice = min(buyQueue.getLast().getPrice(), sellQueue.getFirst().getPrice());
                currentOpeningPrice <= max(buyQueue.getFirst().getPrice(), sellQueue.getLast().getPrice()); currentOpeningPrice++) {
            int currentTradableQuantity = calculateTradableQuantityAccordingTo(currentOpeningPrice);
            if (currentTradableQuantity == maximumTradableQuantity)
                if (abs(currentOpeningPrice - lastTradedPrice) < abs(openingPrice - lastTradedPrice))
                    openingPrice = currentOpeningPrice;
            if (currentTradableQuantity > maximumTradableQuantity) {
                maximumTradableQuantity = currentTradableQuantity;
                openingPrice = currentOpeningPrice;
            }
        }
        return openingPrice;
    }

    public int calculateTradableQuantityAccordingTo(int openingPrice) {
        int tradableQuantity = 0;
        for (Order buyOrder : findTradableBuyOrdersAccordingTo(openingPrice))
            tradableQuantity += buyOrder.getQuantity();
        for (Order sellOrder : findTradableSellOrdersAccordingTo(openingPrice))
            tradableQuantity += sellOrder.getQuantity();
        return tradableQuantity;
    }

    public List<Order> findTradableBuyOrdersAccordingTo(int openingPrice) {
        List<Order> tradableBuyOrders = new ArrayList<>();
        for (Order buyOrder : buyQueue)
            if (buyOrder.getPrice() >= openingPrice)
                tradableBuyOrders.add(buyOrder);
        return tradableBuyOrders;
    }

    public List<Order> findTradableSellOrdersAccordingTo(int openingPrice) {
        List<Order> tradableSellOrders = new ArrayList<>();
        for (Order sellOrder : sellQueue)
            if (sellOrder.getPrice() <= openingPrice)
                tradableSellOrders.add(sellOrder);
        return tradableSellOrders;
    }
}
