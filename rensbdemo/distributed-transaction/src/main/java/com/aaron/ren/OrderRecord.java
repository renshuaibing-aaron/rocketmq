package com.aaron.ren;

import lombok.Data;

@Data
public class OrderRecord {

    private int userId ;
    private String orderId;
    private int buyNum ;
    private int payStatus;
    private int goodId;

}
