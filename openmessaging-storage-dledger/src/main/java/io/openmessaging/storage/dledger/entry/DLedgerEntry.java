package io.openmessaging.storage.dledger.entry;

public class DLedgerEntry {

    public final static int POS_OFFSET = 4 + 4 + 8 + 8;
    public final static int HEADER_SIZE = POS_OFFSET + 8 + 4 + 4 + 4;
    public final static int BODY_OFFSET = HEADER_SIZE + 4;

    //魔数，4字节
    private int magic;
    //条目总长度，包含 Header(协议头) + 消息体，占4字节
    private int size;
    //当前条目的 index，占8字节
    private long index;
    //当前条目所属的 投票轮次，占8字节
    private long term;
    //该条目的物理偏移量，类似于 commitlog 文件的物理偏移量，占8字节
    private long pos; //used to validate data
    //保留字段，当前版本未使用，占4字节
    private int channel; //reserved

    //当前版本未使用，占4字节
    private int chainCrc; //like the block chain, this crc indicates any modification before this entry.
    //body 的 CRC 校验和，用来区分数据是否损坏，占4字节
    private int bodyCrc; //the crc of the body

    //具体消息的内容
    private byte[] body;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getMagic() {
        return magic;
    }

    public void setMagic(int magic) {
        this.magic = magic;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public int getChainCrc() {
        return chainCrc;
    }

    public void setChainCrc(int chainCrc) {
        this.chainCrc = chainCrc;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public int getBodyCrc() {
        return bodyCrc;
    }

    public void setBodyCrc(int bodyCrc) {
        this.bodyCrc = bodyCrc;
    }

    public int computSizeInBytes() {
        size = HEADER_SIZE + 4 + body.length;
        return size;
    }

    public long getPos() {
        return pos;
    }

    public void setPos(long pos) {
        this.pos = pos;
    }

    @Override
    public boolean equals(Object entry) {
        if (entry == null || !(entry instanceof DLedgerEntry)) {
            return false;
        }
        DLedgerEntry other = (DLedgerEntry) entry;
        if (this.size != other.size
            || this.magic != other.magic
            || this.index != other.index
            || this.term != other.term
            || this.channel != other.channel
            || this.pos != other.pos) {
            return false;
        }
        if (body == null) {
            return other.body == null;
        }

        if (other.body == null) {
            return false;
        }
        if (body.length != other.body.length) {
            return false;
        }
        for (int i = 0; i < body.length; i++) {
            if (body[i] != other.body[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int h = 1;
        h = prime * h + size;
        h = prime * h + magic;
        h = prime * h + (int) index;
        h = prime * h + (int) term;
        h = prime * h + channel;
        h = prime * h + (int) pos;
        if (body != null) {
            for (int i = 0; i < body.length; i++) {
                h = prime * h + body[i];
            }
        } else {
            h = prime * h;
        }
        return h;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }
}
