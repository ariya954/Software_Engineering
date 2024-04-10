package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class BrokerCreditTest {
    private Security security;
    public Broker buyer_broker, seller_broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        matcher = new Matcher();
        security = Security.builder().build();
        buyer_broker = Broker.builder().credit(100_000_000L).brokerId(1).build();
        seller_broker = Broker.builder().credit(100_000_000L).brokerId(2).build();
        shareholder = Shareholder.builder().shareholderId(1).build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
            new Order(1, security, Side.BUY, 100, 15700, buyer_broker, shareholder),
            new Order(2, security, Side.BUY, 43, 15500, buyer_broker, shareholder),
            new Order(3, security, Side.BUY, 445, 15450, buyer_broker, shareholder),
            new Order(4, security, Side.BUY, 526, 15450, buyer_broker, shareholder),
            new Order(5, security, Side.BUY, 1000, 15400, buyer_broker, shareholder),
            new Order(6, security, Side.SELL, 300, 15800, seller_broker, shareholder),
            new Order(7, security, Side.SELL, 285, 15810, seller_broker, shareholder),
            new Order(8, security, Side.SELL, 800, 15810, seller_broker, shareholder),
            new Order(9, security, Side.SELL, 340, 15820, seller_broker, shareholder),
            new Order(10, security, Side.SELL, 65, 15820, seller_broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void broker_credit_is_decreased_if_the_new_order_does_not_match_with_any_order() {
        long buyer_broker_initial_credit = 100_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 300, 15000, 1, 1, 0);
        security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - newOrderRq.getPrice() * newOrderRq.getQuantity());
    }

    @Test
    void broker_credit_is_not_changed_if_the_broker_does_not_have_enough_credit_and_the_new_order_does_not_match_with_any_order() {
        long buyer_broker_initial_credit = 100_000_000L;
        buyer_broker.decreaseCreditBy(99_000_000L);
        long buyer_broker_credit_before_giving_the_new_order = buyer_broker_initial_credit - 99_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 300, 15000, 1, 1, 0);
        security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit_before_giving_the_new_order);
    }

    @Test
    void broker_credit_is_decreased_if_the_new_order_matches_with_an_order_and_nothing_remains() {
        long buyer_broker_initial_credit = 100_000_000L;
        long seller_broker_initial_credit = 100_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 200, 15805, 1, 1, 0);
        security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        long price_of_the_matched_order = 15800;
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - price_of_the_matched_order * newOrderRq.getQuantity());
        assertThat(seller_broker.getCredit()).isEqualTo(seller_broker_initial_credit + price_of_the_matched_order * newOrderRq.getQuantity());
    }

    @Test
    void broker_credit_is_not_changed_if_the_broker_does_not_have_enough_credit_and_the_new_order_matches_with_an_order_with_no_remainder() {
        long buyer_broker_initial_credit = 100_000_000L;
        long seller_broker_initial_credit = 100_000_000L;
        buyer_broker.decreaseCreditBy(97_000_000L);
        long buyer_broker_credit_before_giving_the_new_order = buyer_broker_initial_credit - 97_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 200, 15805, 1, 1, 0);
        security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit_before_giving_the_new_order);
        assertThat(seller_broker.getCredit()).isEqualTo(seller_broker_initial_credit);
    }

    @Test
    void broker_credit_is_decreased_if_the_new_order_matches_with_an_order_and_there_is_a_remainder() {
        long buyer_broker_initial_credit = 100_000_000L;
        long seller_broker_initial_credit = 100_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 400, 15805, 1, 1, 0);
        security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        long quantity_of_the_matched_order = 300;
        long price_of_the_matched_order = 15800;
        long remainder = newOrderRq.getQuantity() - quantity_of_the_matched_order;
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit - (price_of_the_matched_order * quantity_of_the_matched_order) - (newOrderRq.getPrice() * remainder));
        assertThat(seller_broker.getCredit()).isEqualTo(seller_broker_initial_credit + price_of_the_matched_order * quantity_of_the_matched_order);
    }

    @Test
    void broker_credit_is_not_changed_if_the_broker_does_not_have_enough_credit_and_the_new_order_matches_with_an_order_and_there_is_a_remainder() {
        long buyer_broker_initial_credit = 100_000_000L;
        long seller_broker_initial_credit = 100_000_000L;
        buyer_broker.decreaseCreditBy(91_000_000L);
        long buyer_broker_credit_before_giving_the_new_order = buyer_broker_initial_credit - 91_000_000L;
        EnterOrderRq newOrderRq = EnterOrderRq.createNewOrderRq(1,  security.getIsin(), 11, LocalDateTime.now(), Side.BUY, 700, 15805, 1, 1, 0);
        security.newOrder(newOrderRq, buyer_broker, shareholder, matcher);
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit_before_giving_the_new_order);
        assertThat(seller_broker.getCredit()).isEqualTo(seller_broker_initial_credit);
    }

    @Test
    void broker_credit_is_decreased_if_the_updated_order_does_not_match_with_any_order() {
        long buyer_broker_initial_credit = 100_000_000L;
        long original_order_value = orders.get(0).getValue();
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1,  security.getIsin(), 1, LocalDateTime.now(), Side.BUY, 300, 15000, 1, 1, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit + original_order_value - updateOrderRq.getPrice() * updateOrderRq.getQuantity());
    }

    @Test
    void broker_credit_is_not_changed_if_the_broker_does_not_have_enough_credit_and_the_updated_order_does_not_match_with_any_order() {
        long buyer_broker_initial_credit = 100_000_000L;
        buyer_broker.decreaseCreditBy(99_000_000L);
        long buyer_broker_credit_before_updating_the_order = buyer_broker_initial_credit - 99_000_000L;
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1,  security.getIsin(), 1, LocalDateTime.now(), Side.BUY, 600, 15000, 1, 1, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit_before_updating_the_order);
    }

    @Test
    void broker_credit_is_decreased_if_the_updated_order_matches_with_an_order_and_nothing_remains() {
        long buyer_broker_initial_credit = 100_000_000L;
        long seller_broker_initial_credit = 100_000_000L;
        long original_order_value = orders.get(0).getValue();
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1,  security.getIsin(), 1, LocalDateTime.now(), Side.BUY, 200, 15805, 1, 1, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        long price_of_the_matched_order = 15800;
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit + original_order_value - price_of_the_matched_order * updateOrderRq.getQuantity());
        assertThat(seller_broker.getCredit()).isEqualTo(seller_broker_initial_credit + price_of_the_matched_order * updateOrderRq.getQuantity());
    }

    @Test
    void broker_credit_is_not_changed_if_the_broker_does_not_have_enough_credit_and_the_updated_order_matches_with_an_order_with_no_remainder() {
        long buyer_broker_initial_credit = 100_000_000L;
        long seller_broker_initial_credit = 100_000_000L;
        buyer_broker.decreaseCreditBy(99_000_000L);
        long buyer_broker_credit_before_updating_the_order = buyer_broker_initial_credit - 99_000_000L;
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1,  security.getIsin(), 1, LocalDateTime.now(), Side.BUY, 200, 15805, 1, 1, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit_before_updating_the_order);
        assertThat(seller_broker.getCredit()).isEqualTo(seller_broker_initial_credit);
    }

    @Test
    void broker_credit_is_decreased_if_the_updated_order_matches_with_an_order_and_there_is_a_remainder() {
        long buyer_broker_initial_credit = 100_000_000L;
        long seller_broker_initial_credit = 100_000_000L;
        long original_order_value = orders.get(0).getValue();
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1,  security.getIsin(), 1, LocalDateTime.now(), Side.BUY, 400, 15805, 1, 1, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        long quantity_of_the_matched_order = 300;
        long price_of_the_matched_order = 15800;
        long remainder = updateOrderRq.getQuantity() - quantity_of_the_matched_order;
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit + original_order_value - (price_of_the_matched_order * quantity_of_the_matched_order) - (updateOrderRq.getPrice() * remainder));
        assertThat(seller_broker.getCredit()).isEqualTo(seller_broker_initial_credit + price_of_the_matched_order * quantity_of_the_matched_order);
    }

    @Test
    void broker_credit_is_not_changed_if_the_broker_does_not_have_enough_credit_and_the_updated_order_matches_with_an_order_and_there_is_a_remainder() {
        long buyer_broker_initial_credit = 100_000_000L;
        long seller_broker_initial_credit = 100_000_000L;
        buyer_broker.decreaseCreditBy(99_000_000L);
        long buyer_broker_credit_before_updating_the_order = buyer_broker_initial_credit - 99_000_000L;
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1,  security.getIsin(), 1, LocalDateTime.now(), Side.BUY, 700, 15805, 1, 1, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_credit_before_updating_the_order);
        assertThat(seller_broker.getCredit()).isEqualTo(seller_broker_initial_credit);
    }

    @Test
    void money_is_backed_to_the_broker_after_deleting_an_order() {
        long buyer_broker_initial_credit = 100_000_000L;
        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, security.getIsin(), Side.BUY, orders.get(0).getOrderId());
        assertThatNoException().isThrownBy(() -> security.deleteOrder(deleteOrderRq));
        assertThat(buyer_broker.getCredit()).isEqualTo(buyer_broker_initial_credit + orders.get(0).getValue());
    }
}