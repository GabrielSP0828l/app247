package com.jefiro.app247.infra.event;

import com.jefiro.app247.domain.model.Order;

public record OrderPaymentRetryEvent(Order order, int attemptNumber) {}
