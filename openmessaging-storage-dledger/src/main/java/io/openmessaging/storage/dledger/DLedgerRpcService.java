package io.openmessaging.storage.dledger;

import io.openmessaging.storage.dledger.protocol.DLedgerProtocol;
import io.openmessaging.storage.dledger.protocol.DLedgerProtocolHander;

/**
 * DLedger Server(节点)之间的网络通信，默认基于Netty实现，其实现类为：DLedgerRpcNettyService
 */
public abstract class DLedgerRpcService implements DLedgerProtocol, DLedgerProtocolHander {

    public abstract void startup();

    public abstract void shutdown();

}
