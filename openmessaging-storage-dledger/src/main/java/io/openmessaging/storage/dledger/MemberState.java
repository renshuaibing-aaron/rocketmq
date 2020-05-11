package io.openmessaging.storage.dledger;

import io.openmessaging.storage.dledger.protocol.DLedgerResponseCode;
import io.openmessaging.storage.dledger.utils.IOUtils;
import io.openmessaging.storage.dledger.utils.PreConditions;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.openmessaging.storage.dledger.MemberState.Role.CANDIDATE;
import static io.openmessaging.storage.dledger.MemberState.Role.FOLLOWER;
import static io.openmessaging.storage.dledger.MemberState.Role.LEADER;

/**
 * 节点状态机，即raft协议中的follower、candidate、leader三种状态的状态机实现
 * 根据当前的状态机状态，执行对应的操作，从raft协议中可知，总共存在3种状态：
 *
 * leader
 * 领导者，主节点，该状态下，需要定时向从节点发送心跳包，用来传播数据、确保其领导地位。
 * follower
 * 从节点，该状态下，会开启定时器，尝试进入到candidate状态，以便发起投票选举，同时一旦收到主节点的心跳包，则重置定时器。
 * candidate
 * 候选者，该状态下的节点会发起投票，尝试选择自己为主节点，选举成功后，不会存在该状态下的节点。
 */
public class MemberState {

    public static final String TERM_PERSIST_FILE = "currterm";
    public static final String TERM_PERSIST_KEY_TERM = "currTerm";
    public static final String TERM_PERSIST_KEY_VOTE_FOR = "voteLeader";
    public static Logger logger = LoggerFactory.getLogger(MemberState.class);
    public final DLedgerConfig dLedgerConfig;
    private final ReentrantLock defaultLock = new ReentrantLock();
    private final String group;
    private final String selfId;
    private final String peers;

    //可见初始值是候选者
    private Role role = CANDIDATE;
    private String leaderId;
    //状态机内部维护的轮次
    private long currTerm = -1;
    private String currVoteFor;
    private long ledgerEndIndex = -1;
    private long ledgerEndTerm = -1;
    private long knownMaxTermInGroup = -1;
    private Map<String, String> peerMap = new HashMap<>();

    public MemberState(DLedgerConfig config) {
        this.group = config.getGroup();
        this.selfId = config.getSelfId();
        this.peers = config.getPeers();
        for (String peerInfo : this.peers.split(";")) {
            peerMap.put(peerInfo.split("-")[0], peerInfo.split("-")[1]);
        }
        this.dLedgerConfig = config;
        loadTerm();
    }

    private void loadTerm() {
        try {
            String data = IOUtils.file2String(dLedgerConfig.getDefaultPath() + File.separator + TERM_PERSIST_FILE);
            Properties properties = IOUtils.string2Properties(data);
            if (properties == null) {
                return;
            }
            if (properties.containsKey(TERM_PERSIST_KEY_TERM)) {
                currTerm = Long.valueOf(String.valueOf(properties.get(TERM_PERSIST_KEY_TERM)));
            }
            if (properties.containsKey(TERM_PERSIST_KEY_VOTE_FOR)) {
                currVoteFor = String.valueOf(properties.get(TERM_PERSIST_KEY_VOTE_FOR));
                if (currVoteFor.length() == 0) {
                    currVoteFor = null;
                }
            }
        } catch (Throwable t) {
            logger.error("Load last term failed", t);
        }
    }

    private void persistTerm() {
        try {
            Properties properties = new Properties();
            properties.put(TERM_PERSIST_KEY_TERM, currTerm);
            properties.put(TERM_PERSIST_KEY_VOTE_FOR, currVoteFor == null ? "" : currVoteFor);
            String data = IOUtils.properties2String(properties);
            IOUtils.string2File(data, dLedgerConfig.getDefaultPath() + File.separator + TERM_PERSIST_FILE);
        } catch (Throwable t) {
            logger.error("Persist curr term failed", t);
        }
    }

    public long currTerm() {
        return currTerm;
    }

    public String currVoteFor() {
        return currVoteFor;
    }

    public synchronized void setCurrVoteFor(String currVoteFor) {
        this.currVoteFor = currVoteFor;
        persistTerm();
    }

    public synchronized long nextTerm() {
        PreConditions.check(role == CANDIDATE, DLedgerResponseCode.ILLEGAL_MEMBER_STATE, "%s != %s", role, CANDIDATE);
        if (knownMaxTermInGroup > currTerm) {
            currTerm = knownMaxTermInGroup;
        } else {
            ++currTerm;
        }
        currVoteFor = null;
        persistTerm();
        return currTerm;
    }

    public synchronized void changeToLeader(long term) {
        PreConditions.check(currTerm == term, DLedgerResponseCode.ILLEGAL_MEMBER_STATE, "%d != %d", currTerm, term);
        this.role = LEADER;
        this.leaderId = selfId;
    }

    public synchronized void changeToFollower(long term, String leaderId) {
        PreConditions.check(currTerm == term, DLedgerResponseCode.ILLEGAL_MEMBER_STATE, "%d != %d", currTerm, term);
        this.role = FOLLOWER;
        this.leaderId = leaderId;
    }

    public synchronized void changeToCandidate(long term) {
        assert term >= currTerm;
        PreConditions.check(term >= currTerm, DLedgerResponseCode.ILLEGAL_MEMBER_STATE, "should %d >= %d", term, currTerm);
        if (term > knownMaxTermInGroup) {
            knownMaxTermInGroup = term;
        }
        //the currTerm should be promoted in handleVote thread
        this.role = CANDIDATE;
        this.leaderId = null;
    }

    public String getSelfId() {
        return selfId;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public String getGroup() {
        return group;
    }

    public String getSelfAddr() {
        return peerMap.get(selfId);
    }

    public String getLeaderAddr() {
        return peerMap.get(leaderId);
    }

    public String getPeerAddr(String peerId) {
        return peerMap.get(peerId);
    }

    public boolean isLeader() {
        return role == LEADER;
    }

    public boolean isFollower() {
        return role == FOLLOWER;
    }

    public boolean isCandidate() {
        return role == CANDIDATE;
    }

    public boolean isQuorum(int num) {
        return num >= ((peerSize() / 2) + 1);
    }

    public int peerSize() {
        return peerMap.size();
    }

    public boolean isPeerMember(String id) {
        return id != null && peerMap.containsKey(id);
    }

    public Map<String, String> getPeerMap() {
        return peerMap;
    }

    //just for test
    public void setCurrTermForTest(long term) {
        PreConditions.check(term >= currTerm, DLedgerResponseCode.ILLEGAL_MEMBER_STATE);
        this.currTerm = term;
    }

    public Role getRole() {
        return role;
    }

    public ReentrantLock getDefaultLock() {
        return defaultLock;
    }

    public void updateLedgerIndexAndTerm(long index, long term) {
        this.ledgerEndIndex = index;
        this.ledgerEndTerm = term;
    }

    public long getLedgerEndIndex() {
        return ledgerEndIndex;
    }

    public long getLedgerEndTerm() {
        return ledgerEndTerm;
    }

    public enum Role {
        UNKNOWN,
        CANDIDATE, //候选者，该状态下的节点会发起投票，尝试选择自己为主节点，选举成功后，不会存在该状态下的节点
        LEADER,  //领导者，主节点，该状态下，需要定时向从节点发送心跳包，用来传播数据、确保其领导地位
        FOLLOWER; //从节点，该状态下，会开启定时器，尝试进入到candidate状态，以便发起投票选举，同时一旦收到主节点的心跳包，则重置定时器
    }
}
