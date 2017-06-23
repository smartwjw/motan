/*
 *  Copyright 2009-2016 Weibo, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.weibo.api.motan.protocol.rest;

import com.weibo.api.motan.common.ChannelState;
import com.weibo.api.motan.common.URLParamType;
import com.weibo.api.motan.exception.MotanServiceException;
import com.weibo.api.motan.rpc.DefaultResponse;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;
import com.weibo.api.motan.transport.AbstractClient;
import com.weibo.api.motan.transport.TransportException;
import com.weibo.api.motan.util.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.engines.factory.ApacheHttpClient4EngineFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author wangjunwei
 * @since 2017-06-07
 */
public class RestClient extends AbstractClient implements StatisticCallback {
    private ResteasyWebTarget target;

    private ConcurrentHashMap<Class<?>, Object> proxyObjectCache = new ConcurrentHashMap<Class<?>, Object>();

    private final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    private IdleConnectionMonitorThread idleConnectionMonitorThread = new IdleConnectionMonitorThread(connectionManager);

    public RestClient(URL url) {
        super(url);

        int maxClientConnection = url.getIntParameter(URLParamType.maxClientConnection.getName(),
                URLParamType.maxClientConnection.getIntValue());

        connectionManager.setMaxTotal(maxClientConnection);
        connectionManager.setDefaultMaxPerRoute(maxClientConnection);

        idleConnectionMonitorThread.start();
    }

    @Override
    public synchronized boolean open() {
        if (isAvailable()) {
            return true;
        }

        final RequestConfig requestConfig = RequestConfig
                .custom()
                .setConnectTimeout(url.getIntParameter(URLParamType.connectTimeout.name(), URLParamType.connectTimeout.getIntValue()))
                .setSocketTimeout(url.getIntParameter(URLParamType.requestTimeout.name(), URLParamType.requestTimeout.getIntValue()))
                .build();

        final SocketConfig socketConfig = SocketConfig.custom()
                .setTcpNoDelay(true)
                .setSoKeepAlive(true)
                .build();

        HttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultSocketConfig(socketConfig)
                .setDefaultRequestConfig(requestConfig)
                .build();

        ResteasyClient
                resteasyClient = new ResteasyClientBuilder()
                .httpEngine(ApacheHttpClient4EngineFactory.create(httpClient))
                .build();

        target = resteasyClient.target("http://" + url.getHost() + ":" + url.getPort());

        LoggerUtil.info("RestClient finish Open: url={}", url);

        // 注册统计回调
        StatsUtil.registryStatisticCallback(this);

        // 设置可用状态
        state = ChannelState.ALIVE;
        return state.isAliveState();
    }

    @Override
    public Response request(Request request) throws TransportException {
        if (!isAvailable()) {
            throw new MotanServiceException("RestClient is unavailable: url=" + url.getUri()
                    + MotanFrameworkUtil.toString(request));
        }

        final String interfaceName = request.getInterfaceName();

        final DefaultResponse defaultResponse = new DefaultResponse();
        defaultResponse.setRequestId(request.getRequestId());
        defaultResponse.setAttachments(request.getAttachments());

        try {
            final Class<?> iface = ReflectUtil.forName(interfaceName);
            final Method method = this.getMethod(request, iface);
            final Object proxy = this.getProxyObject(iface);
            final Object result = method.invoke(proxy, request.getArguments());
            defaultResponse.setValue(result);
        } catch (ClassNotFoundException e) {
            throw new MotanServiceException("RestClient class not found: class = " + interfaceName);
        } catch (NoSuchMethodException e) {
            throw new MotanServiceException("RestClient method not found: class = " + interfaceName + ", method = " + request.getMethodName());
        } catch (IllegalAccessException e) {
            throw new MotanServiceException("RestClient illegal access: class = " + interfaceName + ", method = " + request.getMethodName());
        } catch (InvocationTargetException e) {
            throw new MotanServiceException(e);
        }

        return defaultResponse;
    }

    private Object getProxyObject(Class<?> iface) {
        Object proxy = proxyObjectCache.get(iface);
        if (proxy != null) {
            return proxy;
        } else {
            proxyObjectCache.putIfAbsent(iface, target.proxy(iface));
            proxy = proxyObjectCache.get(iface);
        }
        return proxy;
    }


    private Method getMethod(Request request, Class<?> iface) throws NoSuchMethodException {
        final Object[] arguments = request.getArguments();
        Class<?>[] paramTypes = new Class[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            paramTypes[i] = arguments[i].getClass();
        }

        return iface.getMethod(request.getMethodName(), paramTypes);
    }


    @Override
    public void heartbeat(Request request) {
        // TODO
    }


    @Override
    public synchronized void close() {
        close(0);
    }

    /**
     * 目前close不支持timeout的概念
     */
    @Override
    public synchronized void close(int timeout) {
        if (state.isCloseState()) {
            LoggerUtil.info("RestClient close fail: already close, url={}", url.getUri());
            return;
        }

        // 如果当前nettyClient还没有初始化，那么就没有close的理由。
        if (state.isUnInitState()) {
            LoggerUtil.info("RestClient close Fail: don't need to close because node is unInit state: url={}",
                    url.getUri());
            return;
        }

        try {
            target.getResteasyClient().close();
            connectionManager.close();
            idleConnectionMonitorThread.shutdown();

            // 设置close状态
            state = ChannelState.CLOSE;
            // 解除统计回调的注册
            StatsUtil.unRegistryStatisticCallback(this);
            LoggerUtil.info("RestClient close Success: url={}", url.getUri());
        } catch (Exception e) {
            LoggerUtil.error("RestClient close Error: url=" + url.getUri(), e);
        }

    }

    @Override
    public boolean isClosed() {
        return state.isCloseState();
    }

    @Override
    public boolean isAvailable() {
        return state.isAliveState();
    }

    @Override
    public URL getUrl() {
        return url;
    }

    /**
     * 统计回调接口
     */
    @Override
    public String statisticCallback() {
        return null;
    }


    public static class IdleConnectionMonitorThread extends Thread {
        private final HttpClientConnectionManager connMgr;
        private volatile boolean shutdown;

        public IdleConnectionMonitorThread(HttpClientConnectionManager connMgr) {
            super();
            this.connMgr = connMgr;
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(5000);
                        connMgr.closeIdleConnections(30, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LoggerUtil.info("idle conn monitor thread interrupted.", ex);
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }

}
