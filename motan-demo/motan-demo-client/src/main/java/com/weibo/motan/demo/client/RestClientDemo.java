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
package com.weibo.motan.demo.client;

import com.weibo.motan.demo.service.RestService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Motan Rest Demo Client
 *
 * @author wangjunwei
 * @since 2017-06-22
 */
public class RestClientDemo {

    public static void main(String[] args) throws Exception {
        ApplicationContext ctx = new ClassPathXmlApplicationContext(new String[]{"classpath:motan_demo_client_rest.xml"});

        final RestService restService = ctx.getBean(RestService.class);

        for (int i = 0; i < 10; i++) {
            System.out.println(restService.hello("motan" + i));
            Thread.sleep(1000);
        }

        System.out.println("motan demo is finish.");
        System.exit(0);
    }

}
