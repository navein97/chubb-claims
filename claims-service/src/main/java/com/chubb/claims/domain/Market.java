package com.chubb.claims.domain;

/**
 * The six APAC markets. Currency is derived from the market, never taken from user input,
 * so a claim's reserve and settlement are always in the market's local currency.
 */
public enum Market {
    SG("SGD"),
    MY("MYR"),
    HK("HKD"),
    TH("THB"),
    ID("IDR"),
    PH("PHP");

    private final String currency;

    Market(String currency) {
        this.currency = currency;
    }

    public String currency() {
        return currency;
    }
}
