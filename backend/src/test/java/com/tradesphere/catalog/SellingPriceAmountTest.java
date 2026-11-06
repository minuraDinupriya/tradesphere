package com.tradesphere.catalog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellingPriceAmountTest {
    @Test
    void representsFractionalRupeesExactlyAndPadsToTwoPlaces() {
        assertThat(SellingPriceAmount.parse("0.10").value().add(SellingPriceAmount.parse("0.20").value()).toPlainString()).isEqualTo("0.30");
        assertThat(SellingPriceAmount.parse("12.5").decimalString()).isEqualTo("12.50");
    }

    @Test
    void supportsZeroAndMaximumAmount() {
        assertThat(SellingPriceAmount.parse("0").decimalString()).isEqualTo("0.00");
        assertThat(SellingPriceAmount.parse("999999999999.99").decimalString()).isEqualTo("999999999999.99");
    }

    @Test
    void rejectsNegativeInvalidExcessPrecisionAndOverflowWithoutRounding() {
        for (var amount : new String[]{"-0.01", "1.001", "NaN", "Infinity", "1e3", "", "1,000.00", "1000000000000", " 1.00 "}) {
            assertThatThrownBy(() -> SellingPriceAmount.parse(amount)).as(amount).isInstanceOf(InvalidSellingPriceException.class);
        }
        assertThatThrownBy(() -> SellingPriceAmount.parse(null)).isInstanceOf(InvalidSellingPriceException.class);
    }
}
