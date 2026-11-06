package com.tradesphere.catalog;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException() { super("The product was not found in your organization."); }
}
