/*
 * Copyright 2009-2016 Weibo, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.weibo.motan.demo.server;

import com.weibo.api.motan.common.MotanConstants;
import com.weibo.api.motan.util.MotanSwitcherUtil;
import com.weibo.motan.demo.service.RestService;
import com.weibo.motan.demo.service.User;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class RestServerDemo implements RestService {

    public User hello(String id) {
        System.out.println("receive rest request, param: " + id);
        return new User().setId(id).setName("motan" + id);
    }

    public static void main(String[] args) throws InterruptedException {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath:motan_demo_server_rest.xml");
        System.out.println("rest server start...");

        MotanSwitcherUtil.setSwitcherValue(MotanConstants.REGISTRY_HEARTBEAT_SWITCHER, true);

        Thread.sleep(Long.MAX_VALUE);
    }

}
