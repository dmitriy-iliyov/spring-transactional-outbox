package io.github.outbox.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderLineUnitTest {

    @Test
    @DisplayName("UT constructor() when productId is null should throw NullPointerException")
    void constructor_whenProductIdIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new OrderLine(null, 2, new BigDecimal("10.50")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("productId cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when price is null should throw NullPointerException")
    void constructor_whenPriceIsNull_shouldThrowNullPointerException() {
        // when / then
        assertThatThrownBy(() -> new OrderLine("sku-1", 2, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("price cannot be null");
    }

    @Test
    @DisplayName("UT constructor() when arguments are valid should keep every field it was given")
    void constructor_whenArgumentsAreValid_shouldKeepEveryFieldItWasGiven() {
        // given
        BigDecimal price = new BigDecimal("10.50");

        // when
        OrderLine tested = new OrderLine("sku-1", 2, price);

        // then
        assertThat(tested.productId()).isEqualTo("sku-1");
        assertThat(tested.quantity()).isEqualTo(2);
        assertThat(tested.price()).isEqualTo(price);
    }
}
