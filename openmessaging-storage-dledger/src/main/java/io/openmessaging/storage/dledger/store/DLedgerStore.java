package io.openmessaging.storage.dledger.store;

import io.openmessaging.storage.dledger.MemberState;
import io.openmessaging.storage.dledger.entry.DLedgerEntry;

/**
 * 存储部分主要包含存储映射文件、消息存储格式、刷盘、文件加载与文件恢复、过期文件删除
 * Leader 节点的数据存储主要由 DLedgerStore 的 appendAsLeader 方法实现。DLedger 分别实现了基于内存、基于文件的存储实现
 */
public abstract class DLedgerStore {

    public MemberState getMemberState() {
        return null;
    }

    /**
     * 向主节点追加日志(数据)
     * @param entry
     * @return
     */
    public abstract DLedgerEntry appendAsLeader(DLedgerEntry entry);

    /**
     * 向从节点同步日志
     * @param entry
     * @param leaderTerm
     * @param leaderId
     * @return
     */
    public abstract DLedgerEntry appendAsFollower(DLedgerEntry entry, long leaderTerm, String leaderId);

    /**
     * 根据日志下标查找日志
     * @param index
     * @return
     */
    public abstract DLedgerEntry get(Long index);

    /**
     * 获取已提交的下标
     * @return
     */
    public abstract long getCommittedIndex();

    /**
     * 更新commitedIndex的值，为空实现，由具体的存储子类实现
     * @param term
     * @param committedIndex
     */
    public void updateCommittedIndex(long term, long committedIndex) {

    }

    /**
     * 获取 Leader 当前最大的投票轮次
     * @return
     */
    public abstract long getLedgerEndTerm();

    /**
     * 获取 Leader 下一条日志写入的下标（最新日志的下标）
     * @return
     */
    public abstract long getLedgerEndIndex();

    /**
     * 获取 Leader 第一条消息的下标
     * @return
     */
    public abstract long getLedgerBeginIndex();

    /**
     * 更新 Leader 维护的 ledgerEndIndex 和 ledgerEndTerm
     */
    protected void updateLedgerEndIndexAndTerm() {
        if (getMemberState() != null) {
            getMemberState().updateLedgerIndexAndTerm(getLedgerEndIndex(), getLedgerEndTerm());
        }
    }

    /**
     * 刷写，空方法，由具体子类实现
     */
    public void flush() {

    }

    /**
     * 删除日志，空方法，由具体子类实现
     * @param entry
     * @param leaderTerm
     * @param leaderId
     * @return
     */
    public long truncate(DLedgerEntry entry, long leaderTerm, String leaderId) {
        return -1;
    }

    /**
     * 启动存储管理器，空方法，由具体子类实现
     */
    public void startup() {

    }

    /**
     * 关闭存储管理器，空方法，由具体子类实现
     */
    public void shutdown() {

    }
}
