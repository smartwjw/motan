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

import com.weibo.api.motan.rpc.DefaultRequest;
import com.weibo.api.motan.rpc.Provider;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.util.ReflectUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author wangjunwei
 * @since 2017-06-20
 */
public class RestInvocationHandler<T> implements InvocationHandler {
    private final Provider<T> provider;

    public RestInvocationHandler(Provider<T> provider) {
        this.provider = provider;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        DefaultRequest request = new DefaultRequest();

        request.setArguments(args);
        request.setInterfaceName(provider.getInterface().getName());
        request.setMethodName(method.getName());
        request.setParamtersDesc(ReflectUtil.getMethodParamDesc(method));
        // TODO where did we get request id, from request header, or just generate it in server side?
        request.setRequestId(123);

        Response response = provider.call(request);

        return response.getValue();
    }
}
