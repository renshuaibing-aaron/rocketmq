package io.openmessaging.storage.dledger.protocol;

import io.openmessaging.storage.dledger.entry.DLedgerEntry;

public class PushEntryRequest extends RequestOrResponse {
    private long commitIndex = -1;
    private Type type = Type.APPEND;
    private DLedgerEntry entry;

    public DLedgerEntry getEntry() {
        return entry;
    }

    public void setEntry(DLedgerEntry entry) {
        this.entry = entry;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public long getCommitIndex() {
        return commitIndex;
    }

    public void setCommitIndex(long commitIndex) {
        this.commitIndex = commitIndex;
    }

    public enum Type {
        APPEND, //将日志条目追加到从节点
        COMMIT,//通常，leader 会将提交的索引附加到 append 请求，但是如果 append 请求很少且分散，leader 将发送一个单独的请求来通知从节点提交的索引
        COMPARE, //如果 Leader 发生变化，新的 Leader 需要与他的从节点的日志条目进行比较，以便截断从节点多余的数据
        TRUNCATE; //如果 Leader 通过索引完成日志对比，则 Leader 将发送 TRUNCATE 给它的从节点
    }
}
