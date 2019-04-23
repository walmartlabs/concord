package com.walmartlabs.concord.agentoperator.crd;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import java.util.Map;

public class AgentPoolConfiguration {

    private static final long DEFAULT_SCALING_DELAY_MS = 30000;

    private static final int DEFAULT_MAX_SIZE = 10;
    private static final int DEFAULT_MIN_SIZE = 1;
    private static final int DEFAULT_SIZE = 1;
    private static final int DEFAULT_SIZE_INCREMENT = 2;

    private boolean autoScale = true;
    private long scalingDelayMs = DEFAULT_SCALING_DELAY_MS;
    private int maxSize = DEFAULT_MAX_SIZE;
    private int minSize = DEFAULT_MIN_SIZE;
    private int size = DEFAULT_SIZE;
    private int sizeIncrement = DEFAULT_SIZE_INCREMENT;
    private Map<String, Object> queueSelector;
    private Map<String, Object> configMap;
    private Map<String, Object> pod;

    public boolean isAutoScale() {
        return autoScale;
    }

    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
    }

    public long getScalingDelayMs() {
        return scalingDelayMs;
    }

    public void setScalingDelayMs(long scalingDelayMs) {
        this.scalingDelayMs = scalingDelayMs;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getMinSize() {
        return minSize;
    }

    public void setMinSize(int minSize) {
        this.minSize = minSize;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getSizeIncrement() {
        return sizeIncrement;
    }

    public void setSizeIncrement(int sizeIncrement) {
        this.sizeIncrement = sizeIncrement;
    }

    public Map<String, Object> getQueueSelector() {
        return queueSelector;
    }

    public void setQueueSelector(Map<String, Object> queueSelector) {
        this.queueSelector = queueSelector;
    }

    public Map<String, Object> getConfigMap() {
        return configMap;
    }

    public void setConfigMap(Map<String, Object> configMap) {
        this.configMap = configMap;
    }

    public Map<String, Object> getPod() {
        return pod;
    }

    public void setPod(Map<String, Object> pod) {
        this.pod = pod;
    }
}
