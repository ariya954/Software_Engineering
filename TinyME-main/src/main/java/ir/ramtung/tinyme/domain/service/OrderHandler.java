package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Matcher matcher;
    LinkedList<EnterOrderRq> InActiveOrders = new LinkedList<>();

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            validateEnterOrderRq(enterOrderRq);

            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
            Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());

            if(!acceptEnteredOrder(enterOrderRq, security, broker, shareholder))
                return;

            MatchResult matchResult;
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER) {
                if (OrderCanBeActivated(enterOrderRq, security.getLastTradedPrice())) {
                    matchResult = security.newOrder(enterOrderRq, broker, shareholder, matcher);
                    publishEnteredOrderResult(enterOrderRq, matchResult);
                }
                else {
                    InActiveOrders.add(enterOrderRq);
                    return;
                }
            }
            else {
                if(enterOrderRq.getStopPrice() > 0)
                    updateInActiveOrder(enterOrderRq);
                else {
                    matchResult = security.updateOrder(enterOrderRq, matcher);
                    publishEnteredOrderResult(enterOrderRq, matchResult);
                }
            }

            handleInactiveOrders();

        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            EnterOrderRq InActiveOrder = InActiveOrders.stream().filter(inActiveOrder -> inActiveOrder.getOrderId() == deleteOrderRq.getOrderId()).findFirst().get();
            if(InActiveOrder != null) {
                if(InActiveOrder.getSide() == Side.BUY)
                        brokerRepository.findBrokerById(InActiveOrder.getBrokerId()).increaseCreditBy(InActiveOrder.getPrice() * InActiveOrder.getQuantity());
                InActiveOrders.remove(InActiveOrder);
            }
            else {
                Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
                security.deleteOrder(deleteOrderRq);
            }
            eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    private void updateInActiveOrder(EnterOrderRq enterOrderRq) {
        EnterOrderRq InActiveOrder = InActiveOrders.stream().filter(inActiveOrder -> inActiveOrder.getOrderId() == enterOrderRq.getOrderId()).findFirst().get();
        if((enterOrderRq.getPrice() * enterOrderRq.getQuantity()) > (InActiveOrder.getPrice() * InActiveOrder.getQuantity()))
            brokerRepository.findBrokerById(InActiveOrder.getBrokerId()).decreaseCreditBy((enterOrderRq.getPrice() * enterOrderRq.getQuantity()) - (InActiveOrder.getPrice() * InActiveOrder.getQuantity()));
        else
            brokerRepository.findBrokerById(InActiveOrder.getBrokerId()).increaseCreditBy((InActiveOrder.getPrice() * InActiveOrder.getQuantity()) - (enterOrderRq.getPrice() * enterOrderRq.getQuantity()));
        InActiveOrder.setPrice(enterOrderRq.getPrice());
        InActiveOrder.setQuantity(enterOrderRq.getQuantity());
        InActiveOrder.setStopPrice(enterOrderRq.getStopPrice());
    }

    private void handleInactiveOrders() {
        LinkedList<EnterOrderRq> ActivatedBuyOrders = new LinkedList<>();
        LinkedList<EnterOrderRq> ActivatedSellOrders = new LinkedList<>();

        ExtractActiveOrders(ActivatedBuyOrders, ActivatedSellOrders);

        while((!ActivatedBuyOrders.isEmpty()) || (!ActivatedSellOrders.isEmpty())) {
            Collections.sort(ActivatedBuyOrders, Comparator.comparing(EnterOrderRq::getStopPrice));
            Collections.sort(ActivatedSellOrders, Comparator.comparing(EnterOrderRq::getStopPrice));
            Collections.reverse(ActivatedSellOrders);
            for(EnterOrderRq ActiveBuyOrder : ActivatedBuyOrders) {
                Security security = securityRepository.findSecurityByIsin(ActiveBuyOrder.getSecurityIsin());
                Broker broker = brokerRepository.findBrokerById(ActiveBuyOrder.getBrokerId());
                Shareholder shareholder = shareholderRepository.findShareholderById(ActiveBuyOrder.getShareholderId());
                MatchResult matchResult = security.newOrder(ActiveBuyOrder, broker, shareholder, matcher);
                publishEnteredOrderResult(ActiveBuyOrder, matchResult);
                InActiveOrders.remove(ActiveBuyOrder);
            }
            for(EnterOrderRq ActiveSellOrder : ActivatedSellOrders) {
                Security security = securityRepository.findSecurityByIsin(ActiveSellOrder.getSecurityIsin());
                Broker broker = brokerRepository.findBrokerById(ActiveSellOrder.getBrokerId());
                Shareholder shareholder = shareholderRepository.findShareholderById(ActiveSellOrder.getShareholderId());
                MatchResult matchResult = security.newOrder(ActiveSellOrder, broker, shareholder, matcher);
                publishEnteredOrderResult(ActiveSellOrder, matchResult);
                InActiveOrders.remove(ActiveSellOrder);
            }

            ExtractActiveOrders(ActivatedBuyOrders, ActivatedSellOrders);
        }
    }

    private void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);
        if (enterOrderRq.getMinimumExecutionQuantity() < 0)
            errors.add(Message.MINIMUM_EXECUTION_QUANTITY_IS_NEGATIVE);
        if(enterOrderRq.getMinimumExecutionQuantity() > enterOrderRq.getQuantity())
            errors.add(Message.MINIMUM_EXECUTION_QUANTITY_IS_GREATER_THAN_ORDER_QUANTITY);
        if(enterOrderRq.getStopPrice() > 0)
            if(enterOrderRq.getPeakSize() > 0 || enterOrderRq.getMinimumExecutionQuantity() > 0)
                errors.add(Message.STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG_OR_HAVE_MINIMUM_EXECUTION_QUANTITY);
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        }
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
        if(enterOrderRq.getRequestType().equals(OrderEntryType.UPDATE_ORDER))
            if(enterOrderRq.getStopPrice() == 0)
                if(enterOrderRq.getMinimumExecutionQuantity() != security.getOrderBook().findByOrderId(enterOrderRq.getSide(), enterOrderRq.getOrderId()).getMinimumExecutionQuantity())
                    errors.add(Message.CHANGING_THE_MINIMUM_EXECUTION_QUANTITY_DURING_UPDATING_AN_ORDER_IS_NOT_ALLOWED);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private boolean acceptEnteredOrder(EnterOrderRq enterOrderRq, Security security, Broker broker, Shareholder shareholder) {
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            if(enterOrderRq.getStopPrice() > 0) {
                if(enterOrderRq.getSide() == Side.BUY) {
                    if (!broker.hasEnoughCredit(enterOrderRq.getPrice() * enterOrderRq.getQuantity())) {
                        eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
                        return false;
                    }
                    broker.decreaseCreditBy(enterOrderRq.getPrice() * enterOrderRq.getQuantity());
                }
                else {
                    if(!shareholder.hasEnoughPositionsOn(security, security.getOrderBook().totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity())) {
                        eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
                        return false;
                    }
                }
                eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
            }
        if(enterOrderRq.getRequestType() == OrderEntryType.UPDATE_ORDER)
            if(enterOrderRq.getStopPrice() > 0) {
                if (InActiveOrders.stream().filter(inActiveOrder -> inActiveOrder.getOrderId() == enterOrderRq.getOrderId()).findFirst().get() != null)
                    eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
                else {
                    eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.ORDER_ID_NOT_FOUND)));
                    return false;
                }
            }
        return true;
    }

    private void publishEnteredOrderResult(EnterOrderRq enterOrderRq, MatchResult matchResult) {
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
            return;
        }
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
            return;
        }
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_EXECUTED_QUANTITY) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.THE_EXECUTED_QUANTITY_OF_REQUESTED_ORDER_IS_LESS_THAN_MINIMUM_EXECUTION_QUANTITY)));
            return;
        }

        if(enterOrderRq.getStopPrice() > 0)
            eventPublisher.publish(new OrderActivatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        else {
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
            else
                eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        }

        if (!matchResult.trades().isEmpty()) {
            eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
        }
    }

    private boolean OrderCanBeActivated(EnterOrderRq enterOrderRq, int lastTradedPrice) {
        if(enterOrderRq.getStopPrice() == 0)
            return true;

        if(enterOrderRq.getSide() == Side.BUY)
            if (enterOrderRq.getStopPrice() <= lastTradedPrice)
                return true;
        if(enterOrderRq.getSide() == Side.SELL)
            if(enterOrderRq.getStopPrice() >= lastTradedPrice)
                return true;
        return false;
    }

    private void ExtractActiveOrders(LinkedList<EnterOrderRq> ActivatedBuyOrders, LinkedList<EnterOrderRq> ActivatedSellOrders) {
        ActivatedBuyOrders.clear();
        ActivatedSellOrders.clear();
        for(EnterOrderRq inActiveOrder : InActiveOrders) {
            Security security = securityRepository.findSecurityByIsin(inActiveOrder.getSecurityIsin());
            if (OrderCanBeActivated(inActiveOrder, security.getLastTradedPrice())) {
                if(inActiveOrder.getSide() == Side.BUY)
                    ActivatedBuyOrders.add(inActiveOrder);
                else
                    ActivatedSellOrders.add(inActiveOrder);
            }
        }
    }
}