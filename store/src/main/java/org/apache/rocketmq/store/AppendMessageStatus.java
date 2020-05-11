package org.apache.rocketmq.store;

/**
 * When write a message to the commit log, returns code
 */
public enum AppendMessageStatus {
    PUT_OK,  //追加成功
    END_OF_FILE,  //超过文件大小
    MESSAGE_SIZE_EXCEEDED, //消息长度超过最大允许长度
    PROPERTIES_SIZE_EXCEEDED, //消息超过最大允许长度
    UNKNOWN_ERROR, //未知异常
}
