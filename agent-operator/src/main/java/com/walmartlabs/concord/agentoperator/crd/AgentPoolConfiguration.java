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

import com.walmartlabs.concord.agentoperator.scheduler.DefaultAutoScaler;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;

import java.io.Serializable;
import java.util.Map;

public class AgentPoolConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final long DEFAULT_SCALE_UP_DELAY_MS = 30000;
    private static final long DEFAULT_SCALE_DOWN_DELAY_MS = 180000;

    private static final String ENV_SCALE_UP_DELAY_MS = "SCALE_UP_DELAY_MS";
    private static final String ENV_SCALE_DOWN_DELAY_MS = "SCALE_DOWN_DELAY_MS";
    private static final String ENV_INCREMENT_PERCENTAGE = "INCREMENT_PERCENTAGE";
    private static final String ENV_DECREMENT_PERCENTAGE = "DECREMENT_PERCENTAGE";
    private static final String ENV_INCREMENT_THRESHOLD_FACTOR = "INCREMENT_THRESHOLD_FACTOR";
    private static final String ENV_DECREMENT_THRESHOLD_FACTOR = "DECREMENT_THRESHOLD_FACTOR";

    private static final int DEFAULT_MAX_SIZE = 10;
    private static final int DEFAULT_MIN_SIZE = 1;
    private static final int DEFAULT_SIZE = 1;
    private static final int DEFAULT_SIZE_INCREMENT = 1;

    private static final double DEFAULT_INCREMENT_THRESHOLD_FACTOR = 1.5;
    private static final double DEFAULT_DECREMENT_THRESHOLD_FACTOR = 1.0;
    private static final double DEFAULT_INCREMENT_PERCENTAGE = 50;
    private static final double DEFAULT_DECREMENT_PERCENTAGE = 10;

    private static final int DEFAULT_QUEUE_QUERY_LIMIT = 300;

    private boolean autoScale = true;
    private int maxSize = DEFAULT_MAX_SIZE;
    private int minSize = DEFAULT_MIN_SIZE;
    private int size = DEFAULT_SIZE;

    private String autoScaleStrategy = DefaultAutoScaler.NAME;
    private int sizeIncrement = DEFAULT_SIZE_INCREMENT;

    private int queueQueryLimit = DEFAULT_QUEUE_QUERY_LIMIT;

    /**
     * Minimum time that should elapse between one scale up operation to the next
     */
    private long scaleUpDelayMs = getLongFromEnv(ENV_SCALE_UP_DELAY_MS, DEFAULT_SCALE_UP_DELAY_MS);

    /**
     * Minimum time that should elapse between one scale down operation to the next
     */
    private long scaleDownDelayMs = getLongFromEnv(ENV_SCALE_DOWN_DELAY_MS, DEFAULT_SCALE_DOWN_DELAY_MS);

    /**
     * Percentage of current pool size by which the poolsize has to be increased
     */
    private double percentIncrement = getDoubleFromEnv(ENV_INCREMENT_PERCENTAGE, DEFAULT_INCREMENT_PERCENTAGE);

    /**
     * Percentage of current pool size by which the poolsize has to be decreased
     */
    private double percentDecrement = getDoubleFromEnv(ENV_DECREMENT_PERCENTAGE, DEFAULT_DECREMENT_PERCENTAGE);

    /**
     * Factor that determines the threshold above which the operator can scale up the agent pods
     */

    private double incrementThresholdFactor = getDoubleFromEnv(ENV_INCREMENT_THRESHOLD_FACTOR, DEFAULT_INCREMENT_THRESHOLD_FACTOR);

    /**
     * Factor that determines the threshold below which the operator can scale down the agent pods.
     */
    private double decrementThresholdFactor = getDoubleFromEnv(ENV_DECREMENT_THRESHOLD_FACTOR, DEFAULT_DECREMENT_THRESHOLD_FACTOR);

    private Map<String, Object> queueSelector;
    private ConfigMap configMap;


    private Pod pod;

    public boolean isAutoScale() {
        return autoScale;
    }

    public int getSizeIncrement() {
        return sizeIncrement;
    }

    public void setSizeIncrement(int sizeIncrement) {
        this.sizeIncrement = sizeIncrement;
    }

    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
    }

    public String getAutoScaleStrategy() {
        return autoScaleStrategy;
    }

    public void setAutoScaleStrategy(String autoScaleStrategy) {
        this.autoScaleStrategy = autoScaleStrategy;
    }

    public long getScaleUpDelayMs() {
        return scaleUpDelayMs;
    }

    public void setScaleUpDelayMs(long scaleUpDelayMs) {
        this.scaleUpDelayMs = scaleUpDelayMs;
    }

    public long getScaleDownDelayMs() {
        return scaleDownDelayMs;
    }

    public void setScaleDownDelayMs(long scaleDownDelayMs) {
        this.scaleDownDelayMs = scaleDownDelayMs;
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

    public Map<String, Object> getQueueSelector() {
        return queueSelector;
    }

    public void setQueueSelector(Map<String, Object> queueSelector) {
        this.queueSelector = queueSelector;
    }

    public double getPercentIncrement() {
        return percentIncrement;
    }

    public void setPercentIncrement(double percentIncrement) {
        this.percentIncrement = percentIncrement;
    }

    public double getPercentDecrement() {
        return percentDecrement;
    }

    public void setPercentDecrement(double percentDecrement) {
        this.percentDecrement = percentDecrement;
    }

    public double getIncrementThresholdFactor() {
        return incrementThresholdFactor;
    }

    public void setIncrementThresholdFactor(double incrementThresholdFactor) {
        this.incrementThresholdFactor = incrementThresholdFactor;
    }

    public double getDecrementThresholdFactor() {
        return decrementThresholdFactor;
    }

    public void setDecrementThresholdFactor(double decrementThresholdFactor) {
        this.decrementThresholdFactor = decrementThresholdFactor;
    }

    public int getQueueQueryLimit() {
        return queueQueryLimit;
    }

    public void setQueueQueryLimit(int queueQueryLimit) {
        this.queueQueryLimit = queueQueryLimit;
    }

    public ConfigMap getConfigMap() {
        return configMap;
    }

    public void setConfigMap(ConfigMap configMap) {
        this.configMap = configMap;
    }

    public Pod getPod() {
        return pod;
    }

    public void setPod(Pod pod) {
        this.pod = pod;
    }

    private static long getLongFromEnv(String key, Long defaultValue) {
        String envValue = System.getenv(key);
        return envValue != null ? Long.parseLong(envValue) : defaultValue;
    }

    private static double getDoubleFromEnv(String key, double defaultValue) {
        String envValue = System.getenv(key);
        return envValue != null ? Double.parseDouble(envValue) : defaultValue;
    }

    private static String getStringFromEnv(String key, String defaultValue) {
        String envValue = System.getenv(key);
        return envValue != null ? envValue : defaultValue;
    }
}
