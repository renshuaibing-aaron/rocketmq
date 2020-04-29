/**
 * 基于内存的管理存储实现
 */
package io.openmessaging.storage.dledger.store;

import io.openmessaging.storage.dledger.DLedgerConfig;
import io.openmessaging.storage.dledger.MemberState;
import io.openmessaging.storage.dledger.entry.DLedgerEntry;
import io.openmessaging.storage.dledger.protocol.DLedgerResponseCode;
import io.openmessaging.storage.dledger.utils.PreConditions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DLedgerMemoryStore extends DLedgerStore {

    private static Logger logger = LoggerFactory.getLogger(DLedgerMemoryStore.class);

    private long ledgerBeginIndex = -1;
    private long ledgerEndIndex = -1;
    private long committedIndex = -1;
    private long ledgerEndTerm;
    private Map<Long, DLedgerEntry> cachedEntries = new ConcurrentHashMap<>();

    private DLedgerConfig dLedgerConfig;
    private MemberState memberState;

    public DLedgerMemoryStore(DLedgerConfig dLedgerConfig, MemberState memberState) {
        this.dLedgerConfig = dLedgerConfig;
        this.memberState = memberState;
    }

    @Override
    public DLedgerEntry appendAsLeader(DLedgerEntry entry) {
        PreConditions.check(memberState.isLeader(), DLedgerResponseCode.NOT_LEADER);
        synchronized (memberState) {
            PreConditions.check(memberState.isLeader(), DLedgerResponseCode.NOT_LEADER);
            ledgerEndIndex++;
            committedIndex++;
            ledgerEndTerm = memberState.currTerm();
            entry.setIndex(ledgerEndIndex);
            entry.setTerm(memberState.currTerm());
            if (logger.isDebugEnabled()) {
                logger.debug("[{}] Append as Leader {} {}", memberState.getSelfId(), entry.getIndex(), entry.getBody().length);
            }
            cachedEntries.put(entry.getIndex(), entry);
            if (ledgerBeginIndex == -1) {
                ledgerBeginIndex = ledgerEndIndex;
            }
            updateLedgerEndIndexAndTerm();
            return entry;
        }
    }

    @Override
    public long truncate(DLedgerEntry entry, long leaderTerm, String leaderId) {
        return appendAsFollower(entry, leaderTerm, leaderId).getIndex();
    }

    @Override
    public DLedgerEntry appendAsFollower(DLedgerEntry entry, long leaderTerm, String leaderId) {
        PreConditions.check(memberState.isFollower(), DLedgerResponseCode.NOT_FOLLOWER);
        synchronized (memberState) {
            PreConditions.check(memberState.isFollower(), DLedgerResponseCode.NOT_FOLLOWER);
            PreConditions.check(leaderTerm == memberState.currTerm(), DLedgerResponseCode.INCONSISTENT_TERM);
            PreConditions.check(leaderId.equals(memberState.getLeaderId()), DLedgerResponseCode.INCONSISTENT_LEADER);
            if (logger.isDebugEnabled()) {
                logger.debug("[{}] Append as Follower {} {}", memberState.getSelfId(), entry.getIndex(), entry.getBody().length);
            }
            ledgerEndTerm = memberState.currTerm();
            ledgerEndIndex = entry.getIndex();
            committedIndex = entry.getIndex();
            cachedEntries.put(entry.getIndex(), entry);
            if (ledgerBeginIndex == -1) {
                ledgerBeginIndex = ledgerEndIndex;
            }
            updateLedgerEndIndexAndTerm();
            return entry;
        }

    }

    @Override
    public DLedgerEntry get(Long index) {
        return cachedEntries.get(index);
    }

    @Override
    public long getLedgerEndIndex() {
        return ledgerEndIndex;
    }

    @Override public long getLedgerBeginIndex() {
        return ledgerBeginIndex;
    }

    @Override
    public long getCommittedIndex() {
        return committedIndex;
    }

    @Override
    public long getLedgerEndTerm() {
        return ledgerEndTerm;
    }
}
