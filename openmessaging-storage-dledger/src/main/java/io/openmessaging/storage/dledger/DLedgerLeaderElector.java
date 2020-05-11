package io.openmessaging.storage.dledger;

import com.alibaba.fastjson.JSON;
import io.openmessaging.storage.dledger.protocol.DLedgerResponseCode;
import io.openmessaging.storage.dledger.protocol.HeartBeatRequest;
import io.openmessaging.storage.dledger.protocol.HeartBeatResponse;
import io.openmessaging.storage.dledger.protocol.VoteRequest;
import io.openmessaging.storage.dledger.protocol.VoteResponse;
import io.openmessaging.storage.dledger.utils.DLedgerUtils;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leader选举实现器
 */
public class DLedgerLeaderElector {

    private static Logger logger = LoggerFactory.getLogger(DLedgerLeaderElector.class);

    //随机数生成器，对应raft协议中选举超时时间是一随机数
    private Random random = new Random();
    //配置参数
    private DLedgerConfig dLedgerConfig;
    //节点状态机
    private final MemberState memberState;
    //rpc服务，实现向集群内的节点发送心跳包、投票的RPC实现
    private DLedgerRpcService dLedgerRpcService;

    //as a server handler
    //record the last leader state
    //上次收到心跳包的时间戳
    private long lastLeaderHeartBeatTime = -1;
    //上次发送心跳包的时间戳
    private long lastSendHeartBeatTime = -1;
    //上次成功收到心跳包的时间戳
    private long lastSuccHeartBeatTime = -1;
    //一个心跳包的周期，默认为2s
    private int heartBeatTimeIntervalMs = 2000;
    //允许最大的N个心跳周期内未收到心跳包，状态为Follower的节点只有超过 maxHeartBeatLeak *
    // heartBeatTimeIntervalMs 的时间内未收到主节点的心跳包，才会重新进入 Candidate 状态，重新下一轮的选举
    private int maxHeartBeatLeak = 3;
    //as a client
    //发送下一个心跳包的时间戳
    private long nextTimeToRequestVote = -1;
    //是否应该立即发起投票
    private boolean needIncreaseTermImmediately = false;
    //最小的发送投票间隔时间，默认为300ms
    private int minVoteIntervalMs = 300;
    //最大的发送投票的间隔，默认为1000ms
    private int maxVoteIntervalMs = 1000;

    //注册的节点状态处理器，通过 addRoleChangeHandler 方法添加
    private List<RoleChangeHandler> roleChangeHandlers = new ArrayList<>();

    private VoteResponse.ParseResult lastParseResult = VoteResponse.ParseResult.WAIT_TO_REVOTE;
    //上一次投票的开销
    private long lastVoteCost = 0L;
    //状态机管理器
    private StateMaintainer stateMaintainer = new StateMaintainer("StateMaintainer", logger);

    public DLedgerLeaderElector(DLedgerConfig dLedgerConfig, MemberState memberState, DLedgerRpcService dLedgerRpcService) {
        this.dLedgerConfig = dLedgerConfig;
        this.memberState = memberState;
        this.dLedgerRpcService = dLedgerRpcService;
        refreshIntervals(dLedgerConfig);
    }

    public void startup() {
        System.out.println("==========【启动选举状态管理器】==============");
        //启动状态维护管理器
        stateMaintainer.start();
        //遍历状态改变监听器并启动它，可通过DLedgerLeaderElector 的 addRoleChangeHandler 方法增加状态变化监听器
        for (RoleChangeHandler roleChangeHandler : roleChangeHandlers) {
            //其中的是启动状态管理器线程，其run方法实现：
            roleChangeHandler.startup();
        }
    }

    public void shutdown() {
        stateMaintainer.shutdown();
        for (RoleChangeHandler roleChangeHandler : roleChangeHandlers) {
            roleChangeHandler.shutdown();
        }
    }

    private void refreshIntervals(DLedgerConfig dLedgerConfig) {
        this.heartBeatTimeIntervalMs = dLedgerConfig.getHeartBeatTimeIntervalMs();
        this.maxHeartBeatLeak = dLedgerConfig.getMaxHeartBeatLeak();
        this.minVoteIntervalMs = dLedgerConfig.getMinVoteIntervalMs();
        this.maxVoteIntervalMs = dLedgerConfig.getMaxVoteIntervalMs();
    }

    /**
     *
     * @param request
     * @return
     * @throws Exception
     */
    public CompletableFuture<HeartBeatResponse> handleHeartBeat(HeartBeatRequest request) throws Exception {

        System.out.println("【处理心跳包】");
        if (!memberState.isPeerMember(request.getLeaderId())) {
            logger.warn("[BUG] [HandleHeartBeat] remoteId={} is an unknown member", request.getLeaderId());
            return CompletableFuture.completedFuture(new HeartBeatResponse().term(memberState.currTerm()).code(DLedgerResponseCode.UNKNOWN_MEMBER.getCode()));
        }

        if (memberState.getSelfId().equals(request.getLeaderId())) {
            logger.warn("[BUG] [HandleHeartBeat] selfId={} but remoteId={}", memberState.getSelfId(), request.getLeaderId());
            return CompletableFuture.completedFuture(new HeartBeatResponse().term(memberState.currTerm()).code(DLedgerResponseCode.UNEXPECTED_MEMBER.getCode()));
        }

       // 如果主节点的 term 小于 从节点的term，发送反馈给主节点，告知主节点的 term 已过时；
        // 如果投票轮次相同，并且发送心跳包的节点是该节点的主节点，则返回成功
        if (request.getTerm() < memberState.currTerm()) {
            return CompletableFuture.completedFuture(new HeartBeatResponse().term(memberState.currTerm()).code(DLedgerResponseCode.EXPIRED_TERM.getCode()));
        } else if (request.getTerm() == memberState.currTerm()) {
            if (request.getLeaderId().equals(memberState.getLeaderId())) {
                lastLeaderHeartBeatTime = System.currentTimeMillis();
                return CompletableFuture.completedFuture(new HeartBeatResponse());
            }
        }

        //abnormal case
        //hold the lock to get the latest term and leaderId
        //加锁来处理（这里更多的是从节点第一次收到主节点的心跳包）
        synchronized (memberState) {
            //如果主节的投票轮次小于当前投票轮次，则返回主节点投票轮次过期
            if (request.getTerm() < memberState.currTerm()) {
                return CompletableFuture.completedFuture(new HeartBeatResponse().term(memberState.currTerm()).code(DLedgerResponseCode.EXPIRED_TERM.getCode()));

            //如果投票轮次相同
            } else if (request.getTerm() == memberState.currTerm()) {
                //如果当前节点的主节点字段为空，则使用主节点的ID，并返回成功
                if (memberState.getLeaderId() == null) {
                    changeRoleToFollower(request.getTerm(), request.getLeaderId());
                    return CompletableFuture.completedFuture(new HeartBeatResponse());
                //如果当前节点的主节点就是发送心跳包的节点，则更新上一次收到心跳包的时间戳，并返回成功
                } else if (request.getLeaderId().equals(memberState.getLeaderId())) {
                    lastLeaderHeartBeatTime = System.currentTimeMillis();
                    return CompletableFuture.completedFuture(new HeartBeatResponse());
               //如果从节点的主节点与发送心跳包的节点ID不同，说明有另外一个Leaer，
               // 按道理来说是不会发送的，如果发生，则返回已存在- 主节点，标记该心跳包处理结束
                } else {
                    //this should not happen, but if happened
                    logger.error("[{}][BUG] currTerm {} has leader {}, but received leader {}", memberState.getSelfId(), memberState.currTerm(), memberState.getLeaderId(), request.getLeaderId());
                    return CompletableFuture.completedFuture(new HeartBeatResponse().code(DLedgerResponseCode.INCONSISTENT_LEADER.getCode()));
                }
            //如果主节点的投票轮次大于从节点的投票轮次，则认为从节点并为准备好，则从节点进入Candidate 状态，并立即发起一次投票。
            } else {
                //To make it simple, for larger term, do not change to follower immediately
                //first change to candidate, and notify the state-maintainer thread
                changeRoleToCandidate(request.getTerm());
                needIncreaseTermImmediately = true;
                //TOOD notify
                return CompletableFuture.completedFuture(new HeartBeatResponse().code(DLedgerResponseCode.TERM_NOT_READY.getCode()));
            }
        }
    }

    public void changeRoleToLeader(long term) {
        synchronized (memberState) {
            if (memberState.currTerm() == term) {
                memberState.changeToLeader(term);
                lastSendHeartBeatTime = -1;
                handleRoleChange(term, MemberState.Role.LEADER);
                logger.info("[{}] [ChangeRoleToLeader] from term: {} and currTerm: {}", memberState.getSelfId(), term, memberState.currTerm());
            } else {
                logger.warn("[{}] skip to be the leader in term: {}, but currTerm is: {}", memberState.getSelfId(), term, memberState.currTerm());
            }
        }
    }

    public void changeRoleToCandidate(long term) {
        synchronized (memberState) {
            if (term >= memberState.currTerm()) {
                memberState.changeToCandidate(term);
                handleRoleChange(term, MemberState.Role.CANDIDATE);
                logger.info("[{}] [ChangeRoleToCandidate] from term: {} and currTerm: {}", memberState.getSelfId(), term, memberState.currTerm());
            } else {
                logger.info("[{}] skip to be candidate in term: {}, but currTerm: {}", memberState.getSelfId(), term, memberState.currTerm());
            }
        }
    }

    //just for test
    public void testRevote(long term) {
        changeRoleToCandidate(term);
        lastParseResult = VoteResponse.ParseResult.WAIT_TO_VOTE_NEXT;
        nextTimeToRequestVote = -1;
    }

    public void changeRoleToFollower(long term, String leaderId) {
        logger.info("[{}][ChangeRoleToFollower] from term: {} leaderId: {} and currTerm: {}", memberState.getSelfId(), term, leaderId, memberState.currTerm());
        memberState.changeToFollower(term, leaderId);
        lastLeaderHeartBeatTime = System.currentTimeMillis();
        handleRoleChange(term, MemberState.Role.FOLLOWER);
    }

    /**
     *
     * @param request
     * @param self
     * @return
     */
    public CompletableFuture<VoteResponse> handleVote(VoteRequest request, boolean self) {
        System.out.println("【处理投票】");
        //hold the lock to get the latest term, leaderId, ledgerEndIndex
        synchronized (memberState) {

            //为了逻辑的完整性对其请求进行检验，除非有BUG存在，否则是不会出现下述问题的
            if (!memberState.isPeerMember(request.getLeaderId())) {
                logger.warn("[BUG] [HandleVote] remoteId={} is an unknown member", request.getLeaderId());
                return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.currTerm()).voteResult(VoteResponse.RESULT.REJECT_UNKNOWN_LEADER));
            }
            if (!self && memberState.getSelfId().equals(request.getLeaderId())) {
                logger.warn("[BUG] [HandleVote] selfId={} but remoteId={}", memberState.getSelfId(), request.getLeaderId());
                return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.currTerm()).voteResult(VoteResponse.RESULT.REJECT_UNEXPECTED_LEADER));
            }
            //判断发起节点、响应节点维护的team进行投票“仲裁”，分如下3种情况
            if (request.getTerm() < memberState.currTerm()) {
                //如果发起投票节点的 term 小于当前节点的 term ,此种情况下投拒绝票，也就是说在 raft 协议的世界中，谁的 term 越大，越有话语权。
                return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.currTerm()).voteResult(VoteResponse.RESULT.REJECT_EXPIRED_VOTE_TERM));

                //如果发起投票节点的 term 等于当前节点的 term
            } else if (request.getTerm() == memberState.currTerm()) {
                //如果两者的 term 相等，说明两者都处在同一个投票轮次中，地位平等，接下来看该节点是否已经投过票
                //如果未投票、或已投票给请求节点，则继续后面的逻辑
                if (memberState.currVoteFor() == null) {
                    //let it go
                //如果该节点已存在的Leader节点，则拒绝并告知已存在Leader节点
                } else if (memberState.currVoteFor().equals(request.getLeaderId())) {
                    //repeat just let it go
                 //如果该节点还未有Leader节点，但已经投了其他节点的票，则拒绝请求节点，并告知已投票
                } else {
                    if (memberState.getLeaderId() != null) {
                        return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.currTerm()).voteResult(VoteResponse.RESULT.REJECT_ALREADY__HAS_LEADER));
                    } else {
                        return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.currTerm()).voteResult(VoteResponse.RESULT.REJECT_ALREADY_VOTED));
                    }
                }

             //如果发起投票节点的 term 大于当前节点的 term
             //拒绝请求节点的投票请求，并告知自身还未准备投票，自身会使用请求节点的投票轮次立即进入到Candidate状态。
            } else {
                //stepped down by larger term
                changeRoleToCandidate(request.getTerm());
                needIncreaseTermImmediately = true;
                //only can handleVote when the term is consistent
                return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.currTerm()).voteResult(VoteResponse.RESULT.REJECT_TERM_NOT_READY));
            }

            //assert acceptedTerm is true
            //判断请求节点的 ledgerEndTerm 与当前节点的 ledgerEndTerm，这里主要是判断日志的复制进度
            //如果请求节点的 ledgerEndTerm 小于当前节点的 ledgerEndTerm 则拒绝，其原因是请求节点的日志复制进度比当前节点低，这种情况是不能成为主节点的
            if (request.getLedgerEndTerm() < memberState.getLedgerEndTerm()) {
                return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.currTerm()).voteResult(VoteResponse.RESULT.REJECT_EXPIRED_LEDGER_TERM));

           //如果 ledgerEndTerm 相等，但是 ledgerEndIndex 比当前节点小，则拒绝，原因与上一条相同
            } else if (request.getLedgerEndTerm() == memberState.getLedgerEndTerm() && request.getLedgerEndIndex() < memberState.getLedgerEndIndex()) {
                return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.currTerm()).voteResult(VoteResponse.RESULT.REJECT_SMALL_LEDGER_END_INDEX));
            }

            //如果请求的 term 小于 ledgerEndTerm 以同样的理由拒绝
            if (request.getTerm() < memberState.getLedgerEndTerm()) {
                return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.getLedgerEndTerm()).voteResult(VoteResponse.RESULT.REJECT_TERM_SMALL_THAN_LEDGER));
            }

            //经过层层条件帅选，将宝贵的赞成票投给请求节点
            memberState.setCurrVoteFor(request.getLeaderId());
            return CompletableFuture.completedFuture(new VoteResponse(request).term(memberState.currTerm()).voteResult(VoteResponse.RESULT.ACCEPT));
        }
    }

    /**
     * 遍历集群中的节点，异步发送心跳包
     * @param term
     * @param leaderId
     * @throws Exception
     */
    private void sendHeartbeats(long term, String leaderId) throws Exception {
        final AtomicInteger allNum = new AtomicInteger(1);
        final AtomicInteger succNum = new AtomicInteger(1);
        final AtomicInteger notReadyNum = new AtomicInteger(0);
        final AtomicLong maxTerm = new AtomicLong(-1);
        final AtomicBoolean inconsistLeader = new AtomicBoolean(false);
        final CountDownLatch beatLatch = new CountDownLatch(1);
        long startHeartbeatTimeMs = System.currentTimeMillis();
        //遍历集群中的节点，异步发送心跳包
        for (String id : memberState.getPeerMap().keySet()) {
            if (memberState.getSelfId().equals(id)) {
                continue;
            }
            HeartBeatRequest heartBeatRequest = new HeartBeatRequest();
            heartBeatRequest.setGroup(memberState.getGroup());
            heartBeatRequest.setLocalId(memberState.getSelfId());
            heartBeatRequest.setRemoteId(id);
            heartBeatRequest.setLeaderId(leaderId);
            heartBeatRequest.setTerm(term);
            CompletableFuture<HeartBeatResponse> future = dLedgerRpcService.heartBeat(heartBeatRequest);
            future.whenComplete((HeartBeatResponse x, Throwable ex) -> {
                try {

                    if (ex != null) {
                        throw ex;
                    }
                    //统计心跳包发送响应结果
                    switch (DLedgerResponseCode.valueOf(x.getCode())) {
                        //心跳包成功响应
                        case SUCCESS:
                            succNum.incrementAndGet();
                            break;
                        //主节点的投票 term 小于从节点的投票轮次
                        case EXPIRED_TERM:
                            maxTerm.set(x.getTerm());
                            break;
                        //从节点已经有了新的主节点
                        case INCONSISTENT_LEADER:
                            inconsistLeader.compareAndSet(false, true);
                            break;
                        //从节点未准备好
                        case TERM_NOT_READY:
                            notReadyNum.incrementAndGet();
                            break;
                        default:
                            break;
                    }
                    if (memberState.isQuorum(succNum.get())
                        || memberState.isQuorum(succNum.get() + notReadyNum.get())) {
                        beatLatch.countDown();
                    }
                } catch (Throwable t) {
                    logger.error("Parse heartbeat response failed", t);
                } finally {
                    allNum.incrementAndGet();
                    if (allNum.get() == memberState.peerSize()) {
                        beatLatch.countDown();
                    }
                }
            });
        }
        //对收集的响应结果做仲裁
        beatLatch.await(heartBeatTimeIntervalMs, TimeUnit.MILLISECONDS);
        //如果成功的票数大于进群内的半数，则表示集群状态正常，正常按照心跳包间隔发送心跳包
        if (memberState.isQuorum(succNum.get())) {
            lastSuccHeartBeatTime = System.currentTimeMillis();
        } else {
            logger.info("[{}] Parse heartbeat responses in cost={} term={} allNum={} succNum={} notReadyNum={} inconsistLeader={} maxTerm={} peerSize={} lastSuccHeartBeatTime={}",
                memberState.getSelfId(), DLedgerUtils.elapsed(startHeartbeatTimeMs), term, allNum.get(), succNum.get(), notReadyNum.get(), inconsistLeader.get(), maxTerm.get(), memberState.peerSize(), new Timestamp(lastSuccHeartBeatTime));

          //如果成功的票数加上未准备的投票的节点数量超过集群内的半数，则立即发送心跳包
            if (memberState.isQuorum(succNum.get() + notReadyNum.get())) {
                lastSendHeartBeatTime = -1;
          //如果从节点的投票轮次比主节点的大，则使用从节点的投票轮次，或从节点已经有了另外的主节点，节点状态从 Leader 转换为 Candidate
            } else if (maxTerm.get() > term) {
                changeRoleToCandidate(maxTerm.get());
            } else if (inconsistLeader.get()) {
                changeRoleToCandidate(term);
            } else if (DLedgerUtils.elapsed(lastSuccHeartBeatTime) > maxHeartBeatLeak * heartBeatTimeIntervalMs) {
                changeRoleToCandidate(term);
            }
        }
    }

    /**
     * 经过maintainAsCandidate 投票选举后，被其他节点选举成为领导后，会执行该方法，其他节点的状态还是Candidate，
     * 并在计时器过期后，又尝试去发起选举。成为Leader节点后，该节点会做些什么？
     * @throws Exception
     */
    private void maintainAsLeader() throws Exception {

        System.out.println("【maintainAsLeader】");
        //首先判断上一次发送心跳的时间与当前时间的差值是否大于心跳包发送间隔，如果超过，则说明需要发送心跳包
        if (DLedgerUtils.elapsed(lastSendHeartBeatTime) > heartBeatTimeIntervalMs) {
            long term;
            String leaderId;
            synchronized (memberState) {
                //如果当前不是leader节点，则直接返回，主要是为了二次判断
                if (!memberState.isLeader()) {
                    //stop sending
                    return;
                }
                term = memberState.currTerm();
                leaderId = memberState.getLeaderId();
                //重置心跳包发送计时器
                lastSendHeartBeatTime = System.currentTimeMillis();
            }
            //向集群内的所有节点发送心跳包，稍后会详细介绍心跳包的发送
            sendHeartbeats(term, leaderId);
        }
    }

    /**
     * 当 Candidate 状态的节点在收到主节点发送的心跳包后，会将状态变更为follower
     */
    private void maintainAsFollower() {
        System.out.println("【maintainAsFollower】");
        //如果maxHeartBeatLeak (默认为3)个心跳包周期内未收到心跳，则将状态变更为Candidate。
        if (DLedgerUtils.elapsed(lastLeaderHeartBeatTime) > 2 * heartBeatTimeIntervalMs) {
            synchronized (memberState) {
                if (memberState.isFollower() && (DLedgerUtils.elapsed(lastLeaderHeartBeatTime) > maxHeartBeatLeak * heartBeatTimeIntervalMs)) {
                    logger.info("[{}][HeartBeatTimeOut] lastLeaderHeartBeatTime: {} heartBeatTimeIntervalMs: {} lastLeader={}", memberState.getSelfId(), new Timestamp(lastLeaderHeartBeatTime), heartBeatTimeIntervalMs, memberState.getLeaderId());
                    changeRoleToCandidate(memberState.currTerm());
                }
            }
        }
    }

    /**
     * 发起投票请求
     * @param term  发起投票的节点当前的投票轮次
     * @param ledgerEndTerm 发起投票节点维护的已知的最大投票轮次
     * @param ledgerEndIndex 发起投票节点维护的已知的最大日志条目索引
     * @return
     * @throws Exception
     */
    private List<CompletableFuture<VoteResponse>> voteForQuorumResponses(long term, long ledgerEndTerm,
        long ledgerEndIndex) throws Exception {
        System.out.println("【发起投票请求】");
        List<CompletableFuture<VoteResponse>> responses = new ArrayList<>();
        //遍历集群内的节点集合，准备异步发起投票请求。这个集合在启动的时候指定，不能修改
        for (String id : memberState.getPeerMap().keySet()) {
            //构建投票请求
            VoteRequest voteRequest = new VoteRequest();
            voteRequest.setGroup(memberState.getGroup());
            voteRequest.setLedgerEndIndex(ledgerEndIndex);
            voteRequest.setLedgerEndTerm(ledgerEndTerm);
            voteRequest.setLeaderId(memberState.getSelfId());
            voteRequest.setTerm(term);
            voteRequest.setRemoteId(id);
            CompletableFuture<VoteResponse> voteResponse;

            //如果是发送给自己的，则直接调用handleVote进行投票请求响应，
            // 如果是发送给集群内的其他节点，则通过网络发送投票请求，对端节点调用各自的handleVote对集群进行响应
            if (memberState.getSelfId().equals(id)) {
                voteResponse = handleVote(voteRequest, true);
            } else {
                //async
                voteResponse = dLedgerRpcService.vote(voteRequest);
            }
            responses.add(voteResponse);

        }
        return responses;
    }

    //下一次倒计时：当前时间戳 + 上次投票的开销 + 最小投票间隔(300ms) + （1000- 300 ）之间的随机值
    private long getNextTimeToRequestVote() {
        return System.currentTimeMillis() + lastVoteCost + minVoteIntervalMs + random.nextInt(maxVoteIntervalMs - minVoteIntervalMs);
    }

    private void maintainAsCandidate() throws Exception {
        System.out.println("【maintainAsCandidate】");
        //  nextTimeToRequestVote 下一次发发起的投票的时间，如果当前时间小于该值，说明计时器未过期，此时无需发起投票
        //  needIncreaseTermImmediately
        //     是否应该立即发起投票。如果为true，则忽略计时器，该值默认为false，当收到从主节点的心跳包并且当前状态机的轮次
        //     大于主节点的轮次，说明集群中Leader的投票轮次小于从几点的轮次，应该立即发起新的投票
        //for candidate
        if (System.currentTimeMillis() < nextTimeToRequestVote && !needIncreaseTermImmediately) {
            return;
        }
        //投票轮次
        long term;
        //Leader节点当前的投票轮次
        long ledgerEndTerm;
        //当前日志的最大序列，即下一条日志的开始index，在日志复制部分会详细介绍
        long ledgerEndIndex;

        //初始化team、ledgerEndIndex 、ledgerEndTerm 属性，其实现关键点如下：
        //  如果上一次的投票结果为待下一次投票或应该立即开启投票，并且根据当前状态机获取下一轮的投票轮次，稍后会着重讲解一下状态机轮次的维护机制。
        //  如果上一次的投票结果不是WAIT_TO_VOTE_NEXT(等待下一轮投票)，则投票轮次依然为状态机内部维护的轮次。
        synchronized (memberState) {
            if (!memberState.isCandidate()) {
                return;
            }
            if (lastParseResult == VoteResponse.ParseResult.WAIT_TO_VOTE_NEXT || needIncreaseTermImmediately) {
                long prevTerm = memberState.currTerm();
                term = memberState.nextTerm();
                logger.info("{}_[INCREASE_TERM] from {} to {}", memberState.getSelfId(), prevTerm, term);
                lastParseResult = VoteResponse.ParseResult.WAIT_TO_REVOTE;
            } else {
                term = memberState.currTerm();
            }
            ledgerEndIndex = memberState.getLedgerEndIndex();
            ledgerEndTerm = memberState.getLedgerEndTerm();
        }
        //如果needIncreaseTermImmediately为true，则重置该标记位为false，并重新设置下一次投票超时时间
        if (needIncreaseTermImmediately) {
            nextTimeToRequestVote = getNextTimeToRequestVote();
            needIncreaseTermImmediately = false;
            return;
        }

        //向集群内的其他节点发起投票请，并返回投票结果列表，稍后会重点分析其投票过程。可以预见，接下来就是根据各投票结果进行仲裁。
        long startVoteTimeMs = System.currentTimeMillis();
        final List<CompletableFuture<VoteResponse>> quorumVoteResponses = voteForQuorumResponses(term, ledgerEndTerm, ledgerEndIndex);
        //已知的最大投票轮次
        final AtomicLong knownMaxTermInGroup = new AtomicLong(-1);
        //所有投票票数
        final AtomicInteger allNum = new AtomicInteger(0);
        //有效投票数
        final AtomicInteger validNum = new AtomicInteger(0);
        //获得的投票数
        final AtomicInteger acceptedNum = new AtomicInteger(0);
        //未准备投票的节点数量，如果对端节点的投票轮次小于发起投票的轮次，则认为对端未准备好，对端节点使用本次的轮次进入 - Candidate 状态
        final AtomicInteger notReadyTermNum = new AtomicInteger(0);
        //发起投票的节点的ledgerEndTerm小于对端节点的个数
        final AtomicInteger biggerLedgerNum = new AtomicInteger(0);
        //是否已经存在Leader
        final AtomicBoolean alreadyHasLeader = new AtomicBoolean(false);

        CountDownLatch voteLatch = new CountDownLatch(1);
        for (CompletableFuture<VoteResponse> future : quorumVoteResponses) {
            future.whenComplete((VoteResponse x, Throwable ex) -> {
                try {
                    if (ex != null) {
                        throw ex;
                    }
                    logger.info("[{}][GetVoteResponse] {}", memberState.getSelfId(), JSON.toJSONString(x));
                  //遍历投票结果，收集投票结果
                    if (x.getVoteResult() != VoteResponse.RESULT.UNKNOWN) {
                        //如果投票结果不是UNKNOW，则有效投票数量增1
                        validNum.incrementAndGet();
                    }

                    //统计投票结果
                    synchronized (knownMaxTermInGroup) {
                        switch (x.getVoteResult()) {
                            //赞成票，acceptedNum加一，只有得到的赞成票超过集群节点数量的一半才能成为Leader
                            case ACCEPT:
                                acceptedNum.incrementAndGet();
                                break;
                            //拒绝票，原因是已经投了其他节点的票
                            case REJECT_ALREADY_VOTED:
                                break;
                            //拒绝票，原因是因为集群中已经存在Leaer了。alreadyHasLeader设置为true，无需在判断其他投票结果了，结束本轮投票
                            case REJECT_ALREADY__HAS_LEADER:
                                alreadyHasLeader.compareAndSet(false, true);
                                break;
                            //拒绝票，如果自己维护的term小于远端维护的ledgerEndTerm，则返回该结果，如果对端的team大于自己的team，需要记录对端最大的投票轮次，以便更新自己的投票轮次
                            case REJECT_TERM_SMALL_THAN_LEDGER:
                            //拒绝票，如果自己维护的term小于远端维护的term，更新自己维护的投票轮次
                            case REJECT_EXPIRED_VOTE_TERM:
                                if (x.getTerm() > knownMaxTermInGroup.get()) {
                                    knownMaxTermInGroup.set(x.getTerm());
                                }
                                break;
                            //拒绝票，如果自己维护的 ledgerTerm小于对端维护的ledgerTerm，则返回该结果。如果是此种情况，增加计数器- biggerLedgerNum的值
                            case REJECT_EXPIRED_LEDGER_TERM:
                            //拒绝票，如果对端的ledgerTeam与自己维护的ledgerTeam相等，但是自己维护的dedgerEndIndex小于对端维护的值，返回该值，增加biggerLedgerNum计数器的值
                            case REJECT_SMALL_LEDGER_END_INDEX:
                                biggerLedgerNum.incrementAndGet();
                                break;
                            //拒绝票，对端的投票轮次小于自己的team，则认为对端还未准备好投票，对端使用自己的投票轮次，是自己进入到Candidate状态
                            case REJECT_TERM_NOT_READY:
                                notReadyTermNum.incrementAndGet();
                                break;
                            default:
                                break;

                        }
                    }
                    if (alreadyHasLeader.get()
                        || memberState.isQuorum(acceptedNum.get())
                        || memberState.isQuorum(acceptedNum.get() + notReadyTermNum.get())) {
                        voteLatch.countDown();
                    }
                } catch (Throwable t) {
                    logger.error("Get error when parsing vote response ", t);
                } finally {
                    allNum.incrementAndGet();
                    if (allNum.get() == memberState.peerSize()) {
                        voteLatch.countDown();
                    }
                }
            });

        }
        //等待收集投票结果，并设置超时时间
        try {
            voteLatch.await(3000 + random.nextInt(maxVoteIntervalMs), TimeUnit.MILLISECONDS);
        } catch (Throwable ignore) {

        }

        //根据收集的投票结果判断是否能成为Leader。
        //这里可以看出共有七种投票结果
        //这里我们自定义
        //    讲解关键点之前，我们先定义先将（当前时间戳 + 上次投票的开销 + 最小投票间隔(300ms) + （1000- 300 ）之间的随机值）定义为“ 1个常规计时器”
        lastVoteCost = DLedgerUtils.elapsed(startVoteTimeMs);
        VoteResponse.ParseResult parseResult;

        //1 如果对端的投票轮次大于发起投票的节点，则该节点使用对端的轮次，重新进入到Candidate状态，并且重置投票计时器，其值为“1个常规计时器“
        if (knownMaxTermInGroup.get() > term) {
            parseResult = VoteResponse.ParseResult.WAIT_TO_VOTE_NEXT;
            nextTimeToRequestVote = getNextTimeToRequestVote();
            changeRoleToCandidate(knownMaxTermInGroup.get());

         //2 如果已经存在Leader，该节点重新进入到Candidate,并重置定时器，该定时器的时间： “1个常规计时器” + heartBeatTimeIntervalMs * maxHeartBeatLeak ，其中 heartBeatTimeIntervalMs 为一次心跳间隔时间，
         //maxHeartBeatLeak 为 允许最大丢失的心跳包，即如果Flower节点在多少个心跳周期内未收到心跳包，则认为Leader已下线。
        } else if (alreadyHasLeader.get()) {
            parseResult = VoteResponse.ParseResult.WAIT_TO_VOTE_NEXT;
            nextTimeToRequestVote = getNextTimeToRequestVote() + heartBeatTimeIntervalMs * maxHeartBeatLeak;
        //如果收到的有效票数未超过半数，则重置计时器为“ 1个常规计时器”，然后等待重新投票，注意状态为WAIT_TO_REVOTE，
        // 该状态下的特征是下次投票时不增加投票轮次
        } else if (!memberState.isQuorum(validNum.get())) {
            parseResult = VoteResponse.ParseResult.WAIT_TO_REVOTE;
            nextTimeToRequestVote = getNextTimeToRequestVote();
        //如果得到的赞同票超过半数，则成为Leader。
        } else if (memberState.isQuorum(acceptedNum.get())) {
            parseResult = VoteResponse.ParseResult.PASSED;
        //如果得到的赞成票加上未准备投票的节点数超过半数，则应该立即发起投票，故其结果为REVOTE_IMMEDIATELY
        } else if (memberState.isQuorum(acceptedNum.get() + notReadyTermNum.get())) {
            parseResult = VoteResponse.ParseResult.REVOTE_IMMEDIATELY;
        //如果得到的赞成票加上对端维护的ledgerEndIndex超过半数，则重置计时器，继续本轮次的选举
        } else if (memberState.isQuorum(acceptedNum.get() + biggerLedgerNum.get())) {
            parseResult = VoteResponse.ParseResult.WAIT_TO_REVOTE;
            nextTimeToRequestVote = getNextTimeToRequestVote();
        //其他情况，开启下一轮投票
        } else {
            parseResult = VoteResponse.ParseResult.WAIT_TO_VOTE_NEXT;
            nextTimeToRequestVote = getNextTimeToRequestVote();
        }
        lastParseResult = parseResult;
        logger.info("[{}] [PARSE_VOTE_RESULT] cost={} term={} memberNum={} allNum={} acceptedNum={} notReadyTermNum={} biggerLedgerNum={} alreadyHasLeader={} maxTerm={} result={}",
            memberState.getSelfId(), lastVoteCost, term, memberState.peerSize(), allNum, acceptedNum, notReadyTermNum, biggerLedgerNum, alreadyHasLeader, knownMaxTermInGroup.get(), parseResult);

        //如果投票成功，则状态机状态设置为Leader，然后状态管理在驱动状态时
        // 会调用DLedgerLeaderElector#maintainState时，将进入到maintainAsLeader方法
        if (parseResult == VoteResponse.ParseResult.PASSED) {
            logger.info("[{}] [VOTE_RESULT] has been elected to be the leader in term {}", memberState.getSelfId(), term);
            changeRoleToLeader(term);
        }

    }

    /**
     * 在raft协议中，节点的状态默认为candidate，DLedger的实现从candidate开始，一开始，集群内的所有节点都会尝试发起投票，
     * 这样第一轮要达成选举几乎不太可能
     * todo
     * The core method of maintainer.
     * Run the specified logic according to the current role:
     *  candidate => propose a vote.
     *  leader => send heartbeats to followers, and step down to candidate when quorum followers do not respond.
     *  follower => accept heartbeats, and change to candidate when no heartbeat from leader.
     * @throws Exception
     */
    private void maintainState() throws Exception {
        if (memberState.isLeader()) {
            maintainAsLeader();
        } else if (memberState.isFollower()) {
            maintainAsFollower();
        } else {
            maintainAsCandidate();
        }
    }

    private void handleRoleChange(long term, MemberState.Role role) {
        for (RoleChangeHandler roleChangeHandler : roleChangeHandlers) {
            try {
                roleChangeHandler.handle(term, role);
            } catch (Throwable t) {
                logger.warn("Handle role change failed term={} role={} handler={}", term, role, roleChangeHandler.getClass(), t);
            }
        }
    }

    public void addRoleChangeHandler(RoleChangeHandler roleChangeHandler) {
        if (!roleChangeHandlers.contains(roleChangeHandler)) {
            roleChangeHandlers.add(roleChangeHandler);
        }
    }

    public interface RoleChangeHandler {
        void handle(long term, MemberState.Role role);

        void startup();

        void shutdown();
    }

    /**
     *
     */
    public class StateMaintainer extends ShutdownAbleThread {

        public StateMaintainer(String name, Logger logger) {
            super(name, logger);
        }

        /**
         * 线程的执行体
         */
        @Override public void doWork() {
            try {
                //如果该节点参与Leader选举
                if (DLedgerLeaderElector.this.dLedgerConfig.isEnableLeaderElector()) {
                    //先调用重置定时器
                    DLedgerLeaderElector.this.refreshIntervals(dLedgerConfig);
                    //然后驱动状态机
                    DLedgerLeaderElector.this.maintainState();
                }
                //每执行一次选主，休息10ms
                sleep(10);
            } catch (Throwable t) {
                DLedgerLeaderElector.logger.error("Error in heartbeat", t);
            }
        }

    }

}
