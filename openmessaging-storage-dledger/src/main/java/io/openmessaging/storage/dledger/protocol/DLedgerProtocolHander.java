
package io.openmessaging.storage.dledger.protocol;

import java.util.concurrent.CompletableFuture;

/**
 * Both the RaftLogServer(inbound) and RaftRpcService (outbound) should implement this protocol
 */
public interface DLedgerProtocolHander extends DLedgerClientProtocolHandler {

    CompletableFuture<VoteResponse> handleVote(VoteRequest request) throws Exception;

    CompletableFuture<HeartBeatResponse> handleHeartBeat(HeartBeatRequest request) throws Exception;

    CompletableFuture<PullEntriesResponse> handlePull(PullEntriesRequest request) throws Exception;

    CompletableFuture<PushEntryResponse> handlePush(PushEntryRequest request) throws Exception;

}
