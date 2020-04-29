
package io.openmessaging.storage.dledger.protocol;

import java.util.concurrent.CompletableFuture;

/**
 * Both the RaftLogServer(inbound) and RaftRpcService (outbound) should implement this protocol
 */
public interface DLedgerClientProtocolHandler {

    CompletableFuture<AppendEntryResponse> handleAppend(AppendEntryRequest request) throws Exception;

    CompletableFuture<GetEntriesResponse> handleGet(GetEntriesRequest request) throws Exception;

    CompletableFuture<MetadataResponse> handleMetadata(MetadataRequest request) throws Exception;

}
