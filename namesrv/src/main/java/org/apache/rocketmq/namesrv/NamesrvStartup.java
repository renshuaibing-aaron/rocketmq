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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.srvutil.ServerUtil;
import org.apache.rocketmq.srvutil.ShutdownHookThread;
import org.slf4j.LoggerFactory;

/**
 * 启动入口
 */
public class NamesrvStartup
{

    private static InternalLogger log;
    private static Properties properties = null;
    private static CommandLine commandLine = null;

    public static void main(String[] args)
    {
        main0(args);
    }

    /**
     * 1.解析命令行参数 -c -p参数
     * 2.初始化 Controller
     * @param args
     * @return
     */
    public static NamesrvController main0(String[] args)
    {

        try
        {
            //Stepl ： 首先来解析配置文件,需要填充NettyServerConfig , NameServerConfig 属性值 。
            NamesrvController controller = createNamesrvController(args);
            start(controller);
            String tip = "The Name Server boot success. serializeType=" + RemotingCommand
                    .getSerializeTypeConfigInThisServer();
            log.info(tip);
            System.out.printf("%s%n", tip);
            return controller;
        } catch (Throwable e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        return null;
    }

    /**
     * -c 命令行参数用来指定配置文件的位置; －p 命令行参数用来打印所有配置
     * 项的值。注意,用 －p 参数打印配置项的值之后程序就退出了,这是一个帮助调
     * 试的选项。
     * @param args
     * @return
     * @throws IOException
     * @throws JoranException
     */
    public static NamesrvController createNamesrvController(String[] args) throws IOException, JoranException
    {
        System.setProperty(RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));
        //PackageConflictDetect.detectFastjson();

        Options options = ServerUtil.buildCommandlineOptions(new Options());
        commandLine = ServerUtil.parseCmdLine("mqnamesrv", args, buildCommandlineOptions(options), new PosixParser());
        if (null == commandLine)
        {
            System.exit(-1);
            return null;
        }
       //初始化nameserver和nettyserverconfig的值
        final NamesrvConfig namesrvConfig = new NamesrvConfig();
        final NettyServerConfig nettyServerConfig = new NettyServerConfig();

        //1 ) -c configFile 通过，c 命令指定配置文件的路径 。
        //2 ） 使用“ 一 属 性名 属 性值”，例如一 listenPort 9876 。
        nettyServerConfig.setListenPort(9876);
        if (commandLine.hasOption('c'))
        {
            String file = commandLine.getOptionValue('c');
            if (file != null)
            {
                InputStream in = new BufferedInputStream(new FileInputStream(file));
                properties = new Properties();
                properties.load(in);
                MixAll.properties2Object(properties, namesrvConfig);
                MixAll.properties2Object(properties, nettyServerConfig);

                namesrvConfig.setConfigStorePath(file);

                System.out.printf("load config properties file OK, %s%n", file);
                in.close();
            }
        }

        if (commandLine.hasOption('p'))
        {
            InternalLogger console = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_CONSOLE_NAME);
            MixAll.printObjectProperties(console, namesrvConfig);
            MixAll.printObjectProperties(console, nettyServerConfig);
            System.exit(0);
        }

        MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), namesrvConfig);

        if (null == namesrvConfig.getRocketmqHome())
        {
            System.out.printf("Please set the %s variable in your environment to match the location of the RocketMQ " +
                    "installation%n", MixAll.ROCKETMQ_HOME_ENV);
            System.exit(-2);
        }

        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(lc);
        lc.reset();
        configurator.doConfigure(namesrvConfig.getRocketmqHome() + "/conf/logback_namesrv.xml");

        log = InternalLoggerFactory.getLogger(LoggerName.NAMESRV_LOGGER_NAME);

        MixAll.printObjectProperties(log, namesrvConfig);
        MixAll.printObjectProperties(log, nettyServerConfig);

        //Step2 ：根据启动属性创建 NamesrvController 实例，并初始化该实例 ， NameServerController
        //实例为 NameServer核心控制器。
        final NamesrvController controller = new NamesrvController(namesrvConfig, nettyServerConfig);
        // remember all configs to prevent discard
        controller.getConfiguration().registerConfig(properties);

        return controller;
    }

    public static NamesrvController start(final NamesrvController controller) throws Exception
    {

        if (null == controller)
        {
            throw new IllegalArgumentException("NamesrvController is null");
        }

        //NameServer的初始化
        boolean initResult = controller.initialize();
        if (!initResult)
        {
            controller.shutdown();
            System.exit(-3);
        }
         //当程序退出的时候会调用
        //controller.shutdown 来做退出前的清理工作
        //Step3 ：注册 JVM 钩子函数并启动服务器， 以便监昕 Broker 、消息生产者 的网络请求 。
        /**
         * 如果代码中使用了线程池，一种优雅停
         * 机的方式就是注册一个 JVM 钩子函数， 在 JVM 进程关闭之前，先将线程池关闭 ，及时释
         * 放资源
         */
        Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(log, new Callable <Void>()
        {
            @Override
            public Void call() throws Exception
            {
                controller.shutdown();
                return null;
            }
        }));

        //启动NameServer的Netty服务端（NettyRemotingServer），监听渠道的请求信息。当收到客户端的请求信息之后会初始化一个线程，
        // 并放入线程池中进行处理,该线程调用DefaultRequestProcessor. processRequest方法来处理请求
        controller.start();

        return controller;
    }

    public static void shutdown(final NamesrvController controller)
    {
        controller.shutdown();
    }

    public static Options buildCommandlineOptions(final Options options)
    {
        Option opt = new Option("c", "configFile", true, "Name server config properties file");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("p", "printConfigItem", false, "Print all config item");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }

    public static Properties getProperties()
    {
        return properties;
    }
}
