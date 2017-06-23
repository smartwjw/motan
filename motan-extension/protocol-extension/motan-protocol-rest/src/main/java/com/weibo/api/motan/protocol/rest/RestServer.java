/*
 * Copyright 2009-2016 Weibo, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.weibo.api.motan.protocol.rest;

import com.weibo.api.motan.common.ChannelState;
import com.weibo.api.motan.exception.MotanFrameworkException;
import com.weibo.api.motan.rpc.Provider;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;
import com.weibo.api.motan.transport.AbstractServer;
import com.weibo.api.motan.transport.MessageHandler;
import com.weibo.api.motan.transport.TransportException;
import com.weibo.api.motan.util.LoggerUtil;
import com.weibo.api.motan.util.StatisticCallback;
import com.weibo.api.motan.util.StatsUtil;
import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;

import java.lang.reflect.Proxy;

/**
 * Rest Server
 *
 * @author wangjunwei
 * @since 2017-06-06
 */
public class RestServer extends AbstractServer implements StatisticCallback {
    private final ResteasyDeployment resteasyDeployment = new ResteasyDeployment();
    private final UndertowJaxrsServer undertowJaxrsServer = new UndertowJaxrsServer();
    private volatile boolean isBound = false;

    public RestServer(URL url, MessageHandler messageHandler) {
        super(url);
    }

    @Override
    public Response request(Request request) throws TransportException {
        throw new MotanFrameworkException("RestServer request(Request request) method unsupport: url: " + url);
    }

    @Override
    public synchronized boolean open() {
        if (isAvailable()) {
            LoggerUtil.warn("RestServer ServerChannel already Open: url=" + url);
            return true;
        }

        LoggerUtil.info("RestServer ServerChannel start Open: url=" + url);

        resteasyDeployment.start();

        final DeploymentInfo deploymentInfo = undertowJaxrsServer.undertowDeployment(resteasyDeployment)
                .setContextPath("/")
                .setDeploymentName("motan-rest")
                .setClassLoader(Thread.currentThread().getContextClassLoader());


        undertowJaxrsServer.deploy(deploymentInfo).start(Undertow.builder().addHttpListener(url.getPort(), url.getHost()));

        state = ChannelState.ALIVE;
        isBound = true;

        StatsUtil.registryStatisticCallback(this);
        LoggerUtil.info("RestServer ServerChannel finish Open: url=" + url);

        return state.isAliveState();
    }

    @Override
    public synchronized void close() {
        close(0);
    }

    @Override
    public synchronized void close(int timeout) {
        if (state.isCloseState()) {
            LoggerUtil.info("RestServer close fail: already close, url={}", url.getUri());
            return;
        }

        if (state.isUnInitState()) {
            LoggerUtil.info("RestServer close Fail: don't need to close because node is unInit state: url={}",
                    url.getUri());
            return;
        }

        try {
            resteasyDeployment.stop();
            undertowJaxrsServer.stop();

            // 设置close状态
            state = ChannelState.CLOSE;
            // 取消统计回调的注册
            StatsUtil.unRegistryStatisticCallback(this);
            LoggerUtil.info("RestServer close Success: url={}", url.getUri());
        } catch (Exception e) {
            LoggerUtil.error("RestServer close Error: url=" + url.getUri(), e);
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

    @Override
    public String statisticCallback() {
        return null;
    }

    /**
     * 是否已经绑定端口
     */
    @Override
    public boolean isBound() {
        return isBound;
    }

    public <T> void deploy(Provider<T> provider, String contextPath) {
        // 请求路由被 Resteasy 处理了, 我们只能通过动态代理加入治理逻辑
        final Object proxyInstance = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{provider.getInterface()}, new RestInvocationHandler(provider));

        if (StringUtils.isEmpty(contextPath)) {
            resteasyDeployment.getRegistry().addSingletonResource(proxyInstance);
        } else {
            resteasyDeployment.getRegistry().addSingletonResource(proxyInstance, contextPath);
        }
    }

    public <T> void unDeploy(Provider<T> provider) {
        resteasyDeployment.getRegistry().removeRegistrations(provider.getInterface());
    }

}
