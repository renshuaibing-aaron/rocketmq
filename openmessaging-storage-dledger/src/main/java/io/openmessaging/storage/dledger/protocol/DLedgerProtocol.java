package io.openmessaging.storage.dledger.protocol;

import java.util.concurrent.CompletableFuture;

/**
 * DLedger服务端协议，主要定义如下三个方法。
 *
 * CompletableFuture< VoteResponse> vote(VoteRequest request)
 * 发起投票请求。
 * CompletableFuture< HeartBeatResponse> heartBeat(HeartBeatRequest request)
 * Leader向从节点发送心跳包。
 * CompletableFuture< PullEntriesResponse> pull(PullEntriesRequest request)
 * 拉取日志条目，在日志复制部分会详细介绍。
 * CompletableFuture< PushEntryResponse> push(PushEntryRequest request)
 * 推送日志条件，在日志复制部分会详细介绍。
 */
public interface DLedgerProtocol extends DLedgerClientProtocol {

    CompletableFuture<VoteResponse> vote(VoteRequest request) throws Exception;

    CompletableFuture<HeartBeatResponse> heartBeat(HeartBeatRequest request) throws Exception;

    CompletableFuture<PullEntriesResponse> pull(PullEntriesRequest request) throws Exception;

    CompletableFuture<PushEntryResponse> push(PushEntryRequest request) throws Exception;

}
