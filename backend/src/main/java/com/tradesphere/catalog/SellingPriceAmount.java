package com.tradesphere.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact LKR amount; never construct monetary values through a binary floating-point number. */
public record SellingPriceAmount(BigDecimal value) {
    public SellingPriceAmount {
        if (value == null || value.signum() < 0 || value.precision() - value.scale() > 12) {
            throw new InvalidSellingPriceException();
        }
        try {
            value = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidSellingPriceException();
        }
    }

    public static SellingPriceAmount parse(String amount) {
        if (amount == null || !amount.matches("[0-9]{1,12}(\\.[0-9]{1,2})?")) {
            throw new InvalidSellingPriceException();
        }
        return new SellingPriceAmount(new BigDecimal(amount));
    }

    public String decimalString() { return value.toPlainString(); }
}
