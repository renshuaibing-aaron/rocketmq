package org.apache.rocketmq.broker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Test;

public class BrokerStartupTest {

    private String storePathRootDir = ".";

    @Test
    public void testProperties2SystemEnv() throws NoSuchMethodException, InvocationTargetException,
        IllegalAccessException {
        Properties properties = new Properties();
        Class<BrokerStartup> clazz = BrokerStartup.class;
        Method method = clazz.getDeclaredMethod("properties2SystemEnv", Properties.class);
        method.setAccessible(true);
        System.setProperty("rocketmq.namesrv.domain", "value");
        method.invoke(null, properties);
        Assert.assertEquals("value", System.getProperty("rocketmq.namesrv.domain"));
    }
}