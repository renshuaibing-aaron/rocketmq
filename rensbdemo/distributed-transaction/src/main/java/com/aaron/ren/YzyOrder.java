package com.aaron.ren;

import lombok.Data;

@Data
public class YzyOrder {

    private String orderId;
    private int payStatus;

    private int buyNum;
    private int goodId;
    private int userId;


}
