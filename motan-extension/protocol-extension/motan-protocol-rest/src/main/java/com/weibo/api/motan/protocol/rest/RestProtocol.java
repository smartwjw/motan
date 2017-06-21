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

import com.weibo.api.motan.common.URLParamType;
import com.weibo.api.motan.core.extension.ExtensionLoader;
import com.weibo.api.motan.core.extension.SpiMeta;
import com.weibo.api.motan.exception.MotanFrameworkException;
import com.weibo.api.motan.protocol.AbstractProtocol;
import com.weibo.api.motan.rpc.*;
import com.weibo.api.motan.transport.EndpointFactory;
import com.weibo.api.motan.util.LoggerUtil;
import com.weibo.api.motan.util.MotanFrameworkUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rest protocol
 *
 * @author wangjunwei
 * @since 2017-06-06
 */
@SpiMeta(name = "rest")
public class RestProtocol extends AbstractProtocol {

    // 多个 service 可能在一个端口暴露
    private final ConcurrentHashMap<String, RestServer> ipPortServerMap = new ConcurrentHashMap<String, RestServer>();

    @Override
    protected <T> Exporter<T> createExporter(Provider<T> provider, URL url) {
        return new RestExporter<T>(provider, url);
    }

    @Override
    protected <T> Referer<T> createReferer(Class<T> clz, URL url, URL serviceUrl) {
        throw new MotanFrameworkException("not impl");
    }

    /**
     * rest provider
     *
     * @param <T>
     * @author wangjunwei
     */
    class RestExporter<T> extends AbstractExporter<T> {
        private RestServer restServer;
        private EndpointFactory endpointFactory;

        public RestExporter(Provider<T> provider, URL url) {
            super(provider, url);

            endpointFactory = ExtensionLoader.getExtensionLoader(EndpointFactory.class)
                    .getExtension(url.getParameter(URLParamType.endpointFactory.getName(), "rest"));

            final String ipPort = url.getServerPortStr();

            RestServer server;
            synchronized (ipPortServerMap) {
                server = ipPortServerMap.get(ipPort);
                if (server == null) {
                    server = (RestServer) endpointFactory.createServer(url, null);
                    server.open();
                    ipPortServerMap.put(ipPort, server);
                }
            }

            // TODO make contextPath configurable
            String contextPath = null;
            server.deploy(provider, contextPath);

            this.restServer = server;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void unexport() {
            String protocolKey = MotanFrameworkUtil.getProtocolKey(url);
            Exporter<T> exporter = (Exporter<T>) exporterMap.remove(protocolKey);

            if (exporter != null) {
                exporter.destroy();
            }

            this.restServer.unDeploy(provider);

            LoggerUtil.info("RestExporter unexport Success: url={}", url);
        }

        @Override
        protected boolean doInit() {
            return restServer.open();
        }

        @Override
        public boolean isAvailable() {
            return restServer.isAvailable();
        }

        @Override
        public void destroy() {
            endpointFactory.safeReleaseResource(restServer, url);
            LoggerUtil.info("DefaultRpcExporter destory Success: url={}", url);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        synchronized (ipPortServerMap) {
            for (Map.Entry<String, RestServer> entry : ipPortServerMap.entrySet()) {
                LoggerUtil.info("Closing the rest restServer at " + entry.getKey());
                try {
                    entry.getValue().close();
                } catch (Exception e) {
                    LoggerUtil.warn("Error closing rest restServer", e);
                }
            }

            ipPortServerMap.clear();
        }
    }
}
