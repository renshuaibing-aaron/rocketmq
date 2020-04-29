package io.openmessaging.storage.dledger.protocol;

public class AppendEntryRequest extends RequestOrResponse {

    //待发送的数据
    private byte[] body;

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}
