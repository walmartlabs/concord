package com.walmartlabs.concord.server.events.github;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.server.org.triggers.TriggerEntry;

import javax.ws.rs.core.UriInfo;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface GithubTriggerProcessor {

    void process(String eventName, Payload payload, UriInfo uriInfo, List<Result> result);

    class Result {

        private final Map<String, Object> event;

        private final List<TriggerEntry> triggers;

        public Result(Map<String, Object> event, List<TriggerEntry> triggers) {
            this.event = event;
            this.triggers = triggers;
        }

        public Map<String, Object> event() {
            return event;
        }

        public List<TriggerEntry> triggers() {
            return triggers;
        }

        static Result from(Map<String, Object> event, TriggerEntry trigger) {
            return new Result(event, Collections.singletonList(trigger));
        }

        static Result from(Map<String, Object> event, List<TriggerEntry> triggers) {
            return new Result(event, triggers);
        }
    }
}
