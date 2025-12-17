package com.project.fnb.modules.pos.event;

import com.project.fnb.modules.pos.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderPaidEvent {
    private final Order order;
}