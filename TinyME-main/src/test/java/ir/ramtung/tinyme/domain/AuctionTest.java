package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
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
public class AuctionTest {
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
                new Order(1, security, Side.BUY, 50, 15200, buyer_broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15000, buyer_broker, shareholder),
                new Order(3, security, Side.BUY, 45, 14950, buyer_broker, shareholder),
                new Order(4, security, Side.BUY, 52, 14900, buyer_broker, shareholder),
                new Order(5, security, Side.SELL, 300, 15400, seller_broker, shareholder),
                new Order(6, security, Side.SELL, 300, 15800, seller_broker, shareholder),
                new Order(11, security, Side.SELL, 600, 15900, seller_broker, shareholder),
                new Order(12, security, Side.SELL, 300, 16000, seller_broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void order_is_added_to_queue_and_broker_credit_is_decreased_by_order_value_and_opening_price_is_published_correctly_after_changing_matching_state_to_auction() {
        long buyer_broker_initial_credit = 100_000_000L;
        int initial_size_of_buy_queue_order_book = 4;

        ChangeMatchingStateRq changeMatchingStateRq = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq);
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.AUCTION));

        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 900, 16000, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 13));
        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(1 + initial_size_of_buy_queue_order_book);
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - OrderRq1.getPrice() * OrderRq1.getQuantity());
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 16000, 2400));
    }

    @Test
    void if_two_different_opening_prices_have_maximum_tradable_quantity_the_closer_to_last_traded_price_will_be_chosen() {
        long buyer_broker_initial_credit = 100_000_000L;
        int initial_size_of_buy_queue_order_book = 4;

        ChangeMatchingStateRq changeMatchingStateRq = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq);
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.AUCTION));

        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 900, 16000, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 13));
        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(1 + initial_size_of_buy_queue_order_book);
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - OrderRq1.getPrice() * OrderRq1.getQuantity());
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 16000, 2400));

        EnterOrderRq OrderRq2 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq2);

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 14));
        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(2 + initial_size_of_buy_queue_order_book);
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - OrderRq1.getPrice() * OrderRq1.getQuantity() - OrderRq2.getPrice() * OrderRq2.getQuantity());
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 15900, 2400));
    }

    @Test
    void in_reopening_trades_are_made_correctly_and_broker_credit_is_increased_according_to_the_difference_of_order_price_and_opening_price() {
        long buyer_broker_initial_credit = 100_000_000L;
        int initial_size_of_buy_queue_order_book = 4;

        ChangeMatchingStateRq changeMatchingStateRq1 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq1);

        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 900, 16000, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);

        EnterOrderRq OrderRq2 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq2);

        long current_broker_credit = buyer_broker_initial_credit - OrderRq1.getPrice() * OrderRq1.getQuantity() - OrderRq2.getPrice() * OrderRq2.getQuantity();

        ChangeMatchingStateRq changeMatchingStateRq2 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq2);

        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), security.getOpeningPrice(), 300, 13, 5));
        current_broker_credit = current_broker_credit + (OrderRq1.getPrice() - security.getOpeningPrice()) * 300;

        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), security.getOpeningPrice(), 300, 13, 6));
        current_broker_credit = current_broker_credit + (OrderRq1.getPrice() - security.getOpeningPrice()) * 300;

        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), security.getOpeningPrice(), 300, 13, 11));
        current_broker_credit = current_broker_credit + (OrderRq1.getPrice() - security.getOpeningPrice()) * 300;

        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), security.getOpeningPrice(), 300, 14, 11));
        current_broker_credit = current_broker_credit + (OrderRq2.getPrice() - security.getOpeningPrice()) * 300;

        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(initial_size_of_buy_queue_order_book);
        assertThat(buyer_broker.getCredit()).isEqualTo(current_broker_credit);
    }

    @Test
    void in_reopening_order_is_not_enqueued_and_broker_credit_is_not_decreased_if_there_is_remainder_and_reopening_is_finished_if_there_is_no_tradable_sell_order() {
        long buyer_broker_initial_credit = 100_000_000L;
        int initial_size_of_buy_queue_order_book = 4;

        ChangeMatchingStateRq changeMatchingStateRq1 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq1);

        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);

        EnterOrderRq OrderRq2 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 1500, 16000, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq2);

        long current_broker_credit = buyer_broker_initial_credit - OrderRq1.getPrice() * OrderRq1.getQuantity() - OrderRq2.getPrice() * OrderRq2.getQuantity();

        ChangeMatchingStateRq changeMatchingStateRq2 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq2);

        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), security.getOpeningPrice(), 300, 14, 5));
        current_broker_credit = current_broker_credit + (OrderRq2.getPrice() - security.getOpeningPrice()) * 300;

        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), security.getOpeningPrice(), 300, 14, 6));
        current_broker_credit = current_broker_credit + (OrderRq2.getPrice() - security.getOpeningPrice()) * 300;

        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), security.getOpeningPrice(), 600, 14, 11));
        current_broker_credit = current_broker_credit + (OrderRq2.getPrice() - security.getOpeningPrice()) * 600;

        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(initial_size_of_buy_queue_order_book + 2);
        assertThat(buyer_broker.getCredit()).isEqualTo(current_broker_credit);
    }

    @Test
    void By_reopening_inactive_orders_are_activated_and_added_to_queue_if_the_target_matching_state_is_auction() {
        long buyer_broker_initial_credit = 100_000_000L;
        long initial_size_of_buy_queue_order_book = 4;

        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);

        long buyer_broker_credit = buyer_broker_initial_credit - (15400 * 300);

        EnterOrderRq newStopLimitOrderRq = EnterOrderRq.createNewOrderRq(2,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15800, 1, 1, 0, 0, 15900);
        orderHandler.handleEnterOrder(newStopLimitOrderRq);

        buyer_broker_credit = buyer_broker_credit - newStopLimitOrderRq.getPrice() * newStopLimitOrderRq.getQuantity();

        ChangeMatchingStateRq changeMatchingStateRq1 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq1);

        EnterOrderRq OrderRq2 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 15, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq2);

        long current_broker_credit = buyer_broker_credit - OrderRq2.getPrice() * OrderRq2.getQuantity();

        ChangeMatchingStateRq changeMatchingStateRq2 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        current_broker_credit = current_broker_credit + (OrderRq2.getPrice() - security.getOpeningPrice()) * 300;
        orderHandler.handleChangeMatchingState(changeMatchingStateRq2);

        verify(eventPublisher).publish(new OrderActivatedEvent(2, 14));
        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(initial_size_of_buy_queue_order_book + 1);
        assertThat(buyer_broker.getCredit()).isEqualTo(current_broker_credit);
    }

    @Test
    void By_reopening_inactive_orders_are_activated_and_executed_if_the_target_matching_state_is_continuous() {
        long buyer_broker_initial_credit = 100_000_000L;
        long initial_size_of_buy_queue_order_book = 4;

        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15400, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);

        long buyer_broker_credit = buyer_broker_initial_credit - (15400 * 300);

        EnterOrderRq newStopLimitOrderRq = EnterOrderRq.createNewOrderRq(2,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0, 0, 15900);
        orderHandler.handleEnterOrder(newStopLimitOrderRq);

        buyer_broker_credit = buyer_broker_credit - newStopLimitOrderRq.getPrice() * newStopLimitOrderRq.getQuantity();

        ChangeMatchingStateRq changeMatchingStateRq1 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq1);

        EnterOrderRq OrderRq2 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 15, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq2);

        long current_broker_credit = buyer_broker_credit - OrderRq2.getPrice() * OrderRq2.getQuantity();

        ChangeMatchingStateRq changeMatchingStateRq2 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS);
        current_broker_credit = current_broker_credit + (OrderRq2.getPrice() - security.getOpeningPrice()) * 300;
        orderHandler.handleChangeMatchingState(changeMatchingStateRq2);

        current_broker_credit = current_broker_credit + newStopLimitOrderRq.getPrice() * newStopLimitOrderRq.getQuantity();

        verify(eventPublisher).publish(new OrderActivatedEvent(2, 14));
        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(initial_size_of_buy_queue_order_book);
        assertThat(buyer_broker.getCredit()).isEqualTo(current_broker_credit - newStopLimitOrderRq.getPrice() * newStopLimitOrderRq.getQuantity());
    }

    @Test
    void By_updating_order_in_auction_mode_opening_price_is_published_correctly() {
        long buyer_broker_initial_credit = 100_000_000L;
        long initial_size_of_buy_queue_order_book = 4;

        ChangeMatchingStateRq changeMatchingStateRq1 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq1);

        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 1500, 16000, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);

        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(2,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0);
        orderHandler.handleEnterOrder(updateOrderRq);

        verify(eventPublisher).publish(new OrderUpdatedEvent(2, 13));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 15900, 1500));
        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(initial_size_of_buy_queue_order_book + 1);
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - updateOrderRq.getPrice() * updateOrderRq.getQuantity());
    }

    @Test
    void By_deleting_order_in_auction_mode_opening_price_is_published_correctly() {
        long buyer_broker_initial_credit = 100_000_000L;
        long initial_size_of_buy_queue_order_book = 4;

        ChangeMatchingStateRq changeMatchingStateRq1 = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq1);

        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 1500, 16000, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq1);

        EnterOrderRq OrderRq2 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 14, LocalDateTime.now(), Side.BUY, 300, 15900, 1, 1, 0);
        orderHandler.handleEnterOrder(OrderRq2);

        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(2, security.getIsin(), Side.BUY, 13, LocalDateTime.now());
        orderHandler.handleDeleteOrder(deleteOrderRq);

        verify(eventPublisher).publish(new OrderDeletedEvent(2, 13));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 15900, 1500));
        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(initial_size_of_buy_queue_order_book + 1);
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - OrderRq2.getPrice() * OrderRq2.getQuantity());
    }

    @Test
    void order_is_rejected_if_it_has_minimum_execution_quantity_or_stop_price_in_auction_mode() {
        ChangeMatchingStateRq changeMatchingStateRq = new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq);
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.AUCTION));

        EnterOrderRq OrderRq1 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 900, 16000, 1, 1, 0, 300);
        orderHandler.handleEnterOrder(OrderRq1);
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 13, List.of(Message.ORDER_CANNOT_HAVE_MINIMUM_EXECUTION_QUANTITY_OR_STOP_PRICE_IN_AUCTION_MODE)));

        EnterOrderRq OrderRq2 = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 13, LocalDateTime.now(), Side.BUY, 900, 16000, 1, 1, 0, 300, 1000);
        orderHandler.handleEnterOrder(OrderRq2);
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 13, List.of(Message.ORDER_CANNOT_HAVE_MINIMUM_EXECUTION_QUANTITY_OR_STOP_PRICE_IN_AUCTION_MODE)));
    }

    @Test
    void changing_matching_state_fails_if_the_given_security_Isin_is_invalid() {
        ChangeMatchingStateRq changeMatchingStateRq = new ChangeMatchingStateRq("ABC", MatchingState.AUCTION);
        orderHandler.handleChangeMatchingState(changeMatchingStateRq);
        verify(eventPublisher).publish(new OrderRejectedEvent(0, 0, List.of(Message.UNKNOWN_SECURITY_ISIN)));
    }
}