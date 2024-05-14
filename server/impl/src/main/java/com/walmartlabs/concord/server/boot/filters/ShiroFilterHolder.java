package com.walmartlabs.concord.server.boot.filters;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import org.apache.shiro.web.servlet.ShiroFilter;
import org.eclipse.jetty.ee8.servlet.FilterHolder;

import javax.servlet.annotation.WebFilter;

/**
 * Binds {@link ShiroFilter} to Concord's API root path.
 */
@WebFilter("/*")
public class ShiroFilterHolder extends FilterHolder {

    public ShiroFilterHolder() {
        super(ShiroFilter.class);
    }
}
