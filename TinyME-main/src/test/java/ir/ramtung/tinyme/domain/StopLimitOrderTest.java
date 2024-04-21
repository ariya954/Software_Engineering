package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class StopLimitOrderTest {
    @Autowired
    EventPublisher eventPublisher;
    private OrderHandler orderHandler;
    private SecurityRepository securityRepository;
    private BrokerRepository brokerRepository;
    private ShareholderRepository shareholderRepository;
    private Security security;
    public Broker buyer_broker, seller_broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        securityRepository = new SecurityRepository();
        brokerRepository = new BrokerRepository();
        shareholderRepository = new ShareholderRepository();
        matcher = new Matcher();

        security = Security.builder().build();
        buyer_broker = Broker.builder().credit(100_000_000L).brokerId(1).build();
        seller_broker = Broker.builder().credit(100_000_000L).brokerId(2).build();
        shareholder = Shareholder.builder().shareholderId(1).build();
        shareholder.incPosition(security, 100_000);
        securityRepository.addSecurity(security);
        brokerRepository.addBroker(buyer_broker);
        brokerRepository.addBroker(seller_broker);
        shareholderRepository.addShareholder(shareholder);

        orderHandler = new OrderHandler(securityRepository, brokerRepository, shareholderRepository, eventPublisher, matcher);

        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 100, 15700, buyer_broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, buyer_broker, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, buyer_broker, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, buyer_broker, shareholder),
                new Order(5, security, Side.SELL, 300, 15400, seller_broker, shareholder),
                new Order(6, security, Side.SELL, 300, 15800, seller_broker, shareholder),
                new Order(11, security, Side.SELL, 300, 15900, seller_broker, shareholder),
                new Order(12, security, Side.SELL, 300, 16000, seller_broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void stop_limit_order_is_inactivated_if_stop_price_is_bigger_than_last_traded_price() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);
        long buyer_broker_credit = buyer_broker_initial_credit - (15400 * 300);
        EnterOrderRq newStopLimitOrderRq = EnterOrderRq.createNewOrderRq(2,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0, 0, 16000);
        orderHandler.handleEnterOrder(newStopLimitOrderRq);
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 14));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit - newStopLimitOrderRq.getPrice() * newStopLimitOrderRq.getQuantity());
    }

    @Test
    void stop_limit_order_is_activated_immediately_if_stop_price_is_less_than_last_traded_price() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);
        long buyer_broker_credit = buyer_broker_initial_credit - (15400 * 300);
        EnterOrderRq newStopLimitOrderRq = EnterOrderRq.createNewOrderRq(2,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0, 0, 15000);
        orderHandler.handleEnterOrder(newStopLimitOrderRq);
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 14));
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 14));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit - newStopLimitOrderRq.getPrice() * newStopLimitOrderRq.getQuantity());
    }

    @Test
    void stop_limit_order_is_activated_after_a_order_makes_stop_price_less_than_last_traded_price() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);
        long buyer_broker_credit = buyer_broker_initial_credit - (15400 * 300);
        EnterOrderRq newStopLimitOrderRq = EnterOrderRq.createNewOrderRq(2,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0, 0, 15700);
        orderHandler.handleEnterOrder(newStopLimitOrderRq);
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 14));
        EnterOrderRq OrderRq2 = EnterOrderRq.createNewOrderRq(3,  security.getIsin(), 15, LocalDateTime.now(), Side.BUY, 300, 15800, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq2);
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 14));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit - newStopLimitOrderRq.getPrice() * newStopLimitOrderRq.getQuantity() - (15800 * 300));
    }

    @Test
    void stop_limit_order_is_activated_after_an_activated_order_makes_stop_price_less_than_last_traded_price() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);
        long buyer_broker_credit = buyer_broker_initial_credit - (15400 * 300);
        EnterOrderRq StopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(2,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0, 0, 15700);
        orderHandler.handleEnterOrder(StopLimitOrderRq1);
        EnterOrderRq StopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(3,  security.getIsin(), 15, LocalDateTime.now(), Side.BUY, 300, 16000, 1, 1, 0, 0, 15850);
        orderHandler.handleEnterOrder(StopLimitOrderRq2);
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 14));
        verify(eventPublisher).publish(new OrderAcceptedEvent(3, 15));
        EnterOrderRq OrderRq2 = EnterOrderRq.createNewOrderRq(4,  security.getIsin(), 16, LocalDateTime.now(), Side.BUY, 300, 15800, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq2);
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 14));
        verify(eventPublisher).publish(new OrderActivatedEvent(3, 15));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit - (15800*300) - (15900*300) - (16000*300));
    }

    @Test
    void updating_stop_limit_order_activates_stop_limit_order() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);
        long buyer_broker_credit = buyer_broker_initial_credit - (15400 * 300);
        EnterOrderRq newStopLimitOrderRq = EnterOrderRq.createNewOrderRq(2,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0, 0, 16000);
        orderHandler.handleEnterOrder(newStopLimitOrderRq);
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 14));
        EnterOrderRq updateStopLimitOrderRq = EnterOrderRq.createUpdateOrderRq(3,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15800, 1, 1, 0, 0, 15000);
        orderHandler.handleEnterOrder(updateStopLimitOrderRq);
        verify(eventPublisher).publish(new OrderUpdatedEvent(3, 14));
        verify(eventPublisher).publish(new OrderActivatedEvent(2, 14));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit - updateStopLimitOrderRq.getPrice() * updateStopLimitOrderRq.getQuantity());
    }

    @Test
    void broker_credit_is_increased_after_deleting_stop_limit_order() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq newStopLimitOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0, 0, 16000);
        orderHandler.handleEnterOrder(newStopLimitOrderRq);
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 13));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - (newStopLimitOrderRq.getPrice() * newStopLimitOrderRq.getQuantity()));
        DeleteOrderRq deleteStopLimitOrderRq = new DeleteOrderRq(2, security.getIsin(), Side.BUY, 13, LocalDateTime.now());
        orderHandler.handleDeleteOrder(deleteStopLimitOrderRq);
        verify(eventPublisher).publish(new OrderDeletedEvent(2, 13));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit);
    }

    @Test
    void stop_limit_order_is_invalid_if_it_is_iceberg_or_have_minimum_execution_quantity() {
        EnterOrderRq StopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 50, 0, 15700);
        orderHandler.handleEnterOrder(StopLimitOrderRq1);
        verify(eventPublisher).publish(new OrderRejectedEvent(StopLimitOrderRq1.getRequestId(), StopLimitOrderRq1.getOrderId(), List.of(Message.STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG_OR_HAVE_MINIMUM_EXECUTION_QUANTITY)));
        EnterOrderRq StopLimitOrderRq2 = EnterOrderRq.createNewOrderRq(2,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0, 50, 15700);
        orderHandler.handleEnterOrder(StopLimitOrderRq2);
        verify(eventPublisher).publish(new OrderRejectedEvent(StopLimitOrderRq2.getRequestId(), StopLimitOrderRq2.getOrderId(), List.of(Message.STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG_OR_HAVE_MINIMUM_EXECUTION_QUANTITY)));
    }

    @Test
    void stop_limit_order_cannot_be_accepted_if_broker_has_not_enough_credit() {
        brokerRepository.findBrokerById(1).decreaseCreditBy(99_000_000L);
        EnterOrderRq StopLimitOrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 500, 20000, 1, 1, 0, 0, 15700);
        orderHandler.handleEnterOrder(StopLimitOrderRq1);
        verify(eventPublisher).publish(new OrderRejectedEvent(StopLimitOrderRq1.getRequestId(), StopLimitOrderRq1.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
    }
}