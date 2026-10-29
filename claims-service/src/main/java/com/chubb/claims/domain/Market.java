package com.chubb.claims.domain;

import java.math.BigDecimal;

/**
 * The six APAC markets. Currency is derived from the market, never taken from user input,
 * so a claim's reserve and settlement are always in the market's local currency.
 *
 * Each market also sets how much a claims officer may approve on their own (roughly the same
 * value everywhere, in local currency). Anything above needs a claims manager.
 */
public enum Market {
    SG("SGD", "8000"),
    MY("MYR", "20000"),
    HK("HKD", "40000"),
    TH("THB", "180000"),
    ID("IDR", "80000000"),
    PH("PHP", "300000");

    private final String currency;
    private final BigDecimal handlerApprovalLimit;

    Market(String currency, String handlerApprovalLimit) {
        this.currency = currency;
        this.handlerApprovalLimit = new BigDecimal(handlerApprovalLimit);
    }

    public String currency() {
        return currency;
    }

    public BigDecimal handlerApprovalLimit() {
        return handlerApprovalLimit;
    }
}
