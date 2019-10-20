/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.namesrv;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.common.Configuration;
import org.apache.rocketmq.common.ThreadFactoryImpl;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.namesrv.kvconfig.KVConfigManager;
import org.apache.rocketmq.namesrv.processor.ClusterTestRequestProcessor;
import org.apache.rocketmq.namesrv.processor.DefaultRequestProcessor;
import org.apache.rocketmq.namesrv.routeinfo.BrokerHousekeepingService;
import org.apache.rocketmq.namesrv.routeinfo.RouteInfoManager;
import org.apache.rocketmq.remoting.RemotingServer;
import org.apache.rocketmq.remoting.common.TlsMode;
import org.apache.rocketmq.remoting.netty.NettyRemotingServer;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.netty.TlsSystemConfig;
import org.apache.rocketmq.srvutil.FileWatchService;

/**
 * 用来协调各个模块的代码
 */
public class NamesrvController
{
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME);

    private final NamesrvConfig namesrvConfig;

    private final NettyServerConfig nettyServerConfig;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new
            ThreadFactoryImpl("NSScheduledThread"));
    private final KVConfigManager kvConfigManager;
    private final RouteInfoManager routeInfoManager;

    private RemotingServer remotingServer;

    private BrokerHousekeepingService brokerHousekeepingService;

    private ExecutorService remotingExecutor;

    private Configuration configuration;
    private FileWatchService fileWatchService;

    public NamesrvController(NamesrvConfig namesrvConfig, NettyServerConfig nettyServerConfig)
    {
        this.namesrvConfig = namesrvConfig;
        this.nettyServerConfig = nettyServerConfig;
        this.kvConfigManager = new KVConfigManager(this);
        this.routeInfoManager = new RouteInfoManager();
        this.brokerHousekeepingService = new BrokerHousekeepingService(this);
        this.configuration = new Configuration(log, this.namesrvConfig, this.nettyServerConfig);
        this.configuration.setStorePathFromConfig(this.namesrvConfig, "configStorePath");
    }

    /**
     * 初始化
     * @return
     */
    public boolean initialize()
    {

        //1、KVConfigManager类加载NameServer的配置参数，配置参数的路径是 $HOME /namesrv/kvConfig.json;
        // 将配置参数加载保存到KVConfigManager.configTable:HashMap<String/*namespace*/,HashMap<String/*key*/,String/*value*/>>变量中
        this.kvConfigManager.load();

        //以初始化BrokerHousekeepingService对象为参数初始化NettyRemotingServer对象
        this.remotingServer = new NettyRemotingServer(this.nettyServerConfig, this.brokerHousekeepingService);


        //执行线程池初始化一个默认是8个线程的线程池 (private int serverWorkerThreads ＝8)，
        this.remotingExecutor = Executors.newFixedThreadPool(nettyServerConfig.getServerWorkerThreads(), new
                ThreadFactoryImpl("RemotingExecutorThread_"));


       /* 启 动 负责 通 信 的服 务 remotingServer, remotingServer 监 昕 一些端
        口 ，收到 Broker 、 Client 等发过来的请求后，根据请求的命令，调用不同的
        Processor 来处理 。 这些不 同的处理逻辑被放到上面初始化的线程池中执行，*/
       //注册默认的处理类DefaultRequestProcessor,所有的请求均由该处理类的processRequest方法来处理。
        this.registerProcessor();


        //还有两个定时执行的线程，一个用来扫描失效的 Broker (scanNotActiveBroker)
        //定时任务 I: NameServer 每隔 I Os 扫描一次 Broker ， 移除处于不激活状态的 Broker
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable()
        {

            @Override
            public void run()
            {
                NamesrvController.this.routeInfoManager.scanNotActiveBroker();
            }
        }, 5, 10, TimeUnit.SECONDS);



        //另一个用来打印配置信息（ printAllPeriodically ）
        //定时任务 2: names巳rver 每隔 10 分钟打印一次 KV 配置 。
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable()
        {

            @Override
            public void run()
            {
                NamesrvController.this.kvConfigManager.printAllPeriodically();
            }
        }, 1, 10, TimeUnit.MINUTES);



        if (TlsSystemConfig.tlsMode != TlsMode.DISABLED)
        {
            // Register a listener to reload SslContext
            try
            {
                fileWatchService = new FileWatchService(new String[]{TlsSystemConfig.tlsServerCertPath,
                        TlsSystemConfig.tlsServerKeyPath, TlsSystemConfig.tlsServerTrustCertPath}, new
                        FileWatchService.Listener()
                {
                    boolean certChanged, keyChanged = false;

                    @Override
                    public void onChanged(String path)
                    {
                        if (path.equals(TlsSystemConfig.tlsServerTrustCertPath))
                        {
                            log.info("The trust certificate changed, reload the ssl context");
                            reloadServerSslContext();
                        }
                        if (path.equals(TlsSystemConfig.tlsServerCertPath))
                        {
                            certChanged = true;
                        }
                        if (path.equals(TlsSystemConfig.tlsServerKeyPath))
                        {
                            keyChanged = true;
                        }
                        if (certChanged && keyChanged)
                        {
                            log.info("The certificate and private key changed, reload the ssl context");
                            certChanged = keyChanged = false;
                            reloadServerSslContext();
                        }
                    }

                    private void reloadServerSslContext()
                    {
                        ((NettyRemotingServer) remotingServer).loadSslContext();
                    }
                });
            } catch (Exception e)
            {
                log.warn("FileWatchService created error, can't load the certificate dynamically");
            }
        }

        return true;
    }

    private void registerProcessor()
    {
        if (namesrvConfig.isClusterTest())
        {

            this.remotingServer.registerDefaultProcessor(new ClusterTestRequestProcessor(this, namesrvConfig
                    .getProductEnvName()), this.remotingExecutor);
        } else
        {

            this.remotingServer.registerDefaultProcessor(new DefaultRequestProcessor(this), this.remotingExecutor);
        }
    }

    public void start() throws Exception
    {
        this.remotingServer.start();

        if (this.fileWatchService != null)
        {
            this.fileWatchService.start();
        }
    }

    public void shutdown()
    {
        this.remotingServer.shutdown();
        this.remotingExecutor.shutdown();
        this.scheduledExecutorService.shutdown();

        if (this.fileWatchService != null)
        {
            this.fileWatchService.shutdown();
        }
    }

    public NamesrvConfig getNamesrvConfig()
    {
        return namesrvConfig;
    }

    public NettyServerConfig getNettyServerConfig()
    {
        return nettyServerConfig;
    }

    public KVConfigManager getKvConfigManager()
    {
        return kvConfigManager;
    }

    public RouteInfoManager getRouteInfoManager()
    {
        return routeInfoManager;
    }

    public RemotingServer getRemotingServer()
    {
        return remotingServer;
    }

    public void setRemotingServer(RemotingServer remotingServer)
    {
        this.remotingServer = remotingServer;
    }

    public Configuration getConfiguration()
    {
        return configuration;
    }
}
