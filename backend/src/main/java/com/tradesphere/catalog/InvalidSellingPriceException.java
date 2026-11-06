package com.tradesphere.catalog;

public class InvalidSellingPriceException extends RuntimeException {
    public InvalidSellingPriceException() {
        super("Enter an LKR amount from 0 to 999999999999.99 with at most two decimal places.");
    }
}
