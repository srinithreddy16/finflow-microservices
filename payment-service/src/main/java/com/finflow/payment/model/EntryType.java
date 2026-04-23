package com.finflow.payment.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EntryType {
    DEBIT("Money leaving the account"),
    CREDIT("Money entering the account");

    private final String description;
}
