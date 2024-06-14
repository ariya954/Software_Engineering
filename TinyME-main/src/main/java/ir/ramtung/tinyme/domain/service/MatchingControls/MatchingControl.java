package ir.ramtung.tinyme.domain.service.MatchingControls;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Trade;

public interface MatchingControl {
    default boolean canAcceptMatching(Order order, MatchResult matchResult) { return true; }
    default void matchingAccepted(Order order, MatchResult matchResult) {}
    default boolean canAcceptTrade(Order order, Trade trade) { return true; }
    default void tradeAccepted(Order order, Trade trade) {}
}