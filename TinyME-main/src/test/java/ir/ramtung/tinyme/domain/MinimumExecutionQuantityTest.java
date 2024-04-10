package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
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
public class MinimumExecutionQuantityTest {
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
            new Order(7, security, Side.SELL, 285, 15810, seller_broker, shareholder),
            new Order(8, security, Side.SELL, 800, 15810, seller_broker, shareholder),
            new Order(9, security, Side.SELL, 340, 15820, seller_broker, shareholder),
            new Order(10, security, Side.SELL, 65, 15830, seller_broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void broker_credit_is_not_changed_if_the_executed_quantity_is_less_than_minimum_execution_quantity() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 500, 15420, 1, 1, 0, 400);
        MatchResult matchResult = security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        assertThat(matchResult.outcome().equals(MatchingOutcome.NOT_ENOUGH_EXECUTED_QUANTITY));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit);
    }

    @Test
    void broker_credit_is_decreased_if_the_executed_quantity_is_equal_to_minimum_execution_quantity_and_there_is_a_remainder() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 500, 15420, 1, 1, 0, 300);
        MatchResult matchResult = security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        assertThat(matchResult.outcome().equals(MatchingOutcome.EXECUTED));
        long quantity_of_the_matched_order = 300;
        long price_of_the_matched_order = 15400;
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - (price_of_the_matched_order * quantity_of_the_matched_order) - (newOrderRq.getPrice() * matchResult.remainder().getQuantity()));
    }

    @Test
    void broker_credit_is_decreased_if_the_executed_quantity_is_more_than_minimum_execution_quantity_and_nothing_remains() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 300, 15420, 1, 1, 0, 200);
        MatchResult matchResult = security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        assertThat(matchResult.outcome().equals(MatchingOutcome.EXECUTED));
        long quantity_of_the_matched_order = 300;
        long price_of_the_matched_order = 15400;
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - (price_of_the_matched_order * quantity_of_the_matched_order));
    }

    @Test
    void broker_credit_is_decreased_if_the_executed_quantity_is_more_than_minimum_execution_quantity_and_there_is_a_remainder() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 500, 15420, 1, 1, 0, 200);
        MatchResult matchResult = security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        assertThat(matchResult.outcome().equals(MatchingOutcome.EXECUTED));
        long quantity_of_the_matched_order = 300;
        long price_of_the_matched_order = 15400;
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - (price_of_the_matched_order * quantity_of_the_matched_order) - (newOrderRq.getPrice() * matchResult.remainder().getQuantity()));
    }

    @Test
    void new_order_is_rejected_if_its_minimum_execution_quantity_is_less_than_zero() {
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 500, 15420, 1, 1, 0, -5);
        orderHandler.handleEnterOrder(newOrderRq);
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 11, List.of(Message.MINIMUM_EXECUTION_QUANTITY_IS_NEGATIVE)));
    }

    @Test
    void new_order_is_rejected_if_its_minimum_execution_quantity_is_greater_than_its_quantity() {
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 500, 15420, 1, 1, 0, 700);
        orderHandler.handleEnterOrder(newOrderRq);
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 11, List.of(Message.MINIMUM_EXECUTION_QUANTITY_IS_GREATER_THAN_ORDER_QUANTITY)));
    }

    @Test
    void update_order_is_rejected_if_its_minimum_execution_quantity_is_different_from_the_original_minimum_execution_quantity() {
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 500, 15420, 1, 1, 0, 200);
        orderHandler.handleEnterOrder(newOrderRq);
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(2,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 500, 15420, 1, 1, 0, 100);
        orderHandler.handleEnterOrder(updateOrderRq);
        verify(eventPublisher).publish(new OrderRejectedEvent(2, 11, List.of(Message.CHANGING_THE_MINIMUM_EXECUTION_QUANTITY_DURING_UPDATING_AN_ORDER_IS_NOT_ALLOWED)));
    }
}