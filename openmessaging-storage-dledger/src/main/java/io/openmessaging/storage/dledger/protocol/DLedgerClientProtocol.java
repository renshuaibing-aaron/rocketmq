package io.openmessaging.storage.dledger.protocol;

import java.util.concurrent.CompletableFuture;

/**
 * DLedger客户端协议，主要定义如下三个方法，在后面的日志复制部分会重点阐述。
 *
 * CompletableFuture< GetEntriesResponse> get(GetEntriesRequest request)
 * 客户端从服务器获取日志条目（获取数据）
 * CompletableFuture< AppendEntryResponse> append(AppendEntryRequest request)
 * 客户端向服务器追加日志（存储数据）
 * CompletableFuture< MetadataResponse> metadata(MetadataRequest request)
 * 获取元数据。
 */
public interface DLedgerClientProtocol {

    CompletableFuture<GetEntriesResponse> get(GetEntriesRequest request) throws Exception;

    CompletableFuture<AppendEntryResponse> append(AppendEntryRequest request) throws Exception;

    CompletableFuture<MetadataResponse> metadata(MetadataRequest request) throws Exception;

}
