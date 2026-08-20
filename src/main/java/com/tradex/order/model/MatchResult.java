package com.tradex.order.model;

import java.util.List;

public record MatchResult(
    BookOrder incomingOrder,
    List<Fill> fills,
    boolean fullyMatched
) {}
