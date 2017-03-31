/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hdl.caffe.yarn.app;

public class CaffeApplication {

    public static final String OPT_CAFFE_APPNAME = "appname";
    public static final String OPT_CAFFE_PRIORITY = "priority";
    public static final String OPT_CAFFE_QUEUE = "queue";
    public static final String OPT_CAFFE_MASTER_MEMORY = "master_memory";
    public static final String OPT_CAFFE_MASTER_VCORES = "master_vcores";
    public static final String OPT_CAFFE_CONTAINER_MEMORY = "container_memory";
    public static final String OPT_CAFFE_CONTAINER_VCORES = "container_vcores";
    public static final String OPT_CAFFE_LOG_PROPERTIES = "log_properties";
    public static final String OPT_CAFFE_ATTEMPT_FAILURES_VALIDITY_INTERVAL = "attempt_failures_validity_interval";
    public static final String OPT_CAFFE_NODE_LABEL_EXPRESSION = "node_label_expression";
    public static final String OPT_CAFFE_CONTAINER_RETRY_POLICY = "container_retry_policy";
    public static final String OPT_CAFFE_CONTAINER_RETRY_ERROR_CODES = "container_retry_error_codes";
    public static final String OPT_CAFFE_CONTAINER_MAX_RETRIES = "container_max_retries";
    public static final String OPT_CAFFE_CONTAINER_RETRY_INTERVAL = "container_retry_interval";

    public static final String OPT_CAFFE_APP_ATTEMPT_ID = "app_attempt_id";

    public static final String OPT_CAFFE_PROCESSOR_JAR = "caffe_processor_jar";
    public static final String OPT_CAFFE_PROCESSOR_NUM = "num";
    public static final String OPT_CAFFE_PROCESSOR_SOLVER = "conf";
    public static final String OPT_CAFFE_PROCESSOR_TRAIN = "train";
    public static final String OPT_CAFFE_PROCESSOR_FEATURES = "feature";
    public static final String OPT_CAFFE_PROCESSOR_LABEL = "label";
    public static final String OPT_CAFFE_PROCESSOR_MODEL = "model";
    public static final String OPT_CAFFE_PROCESSOR_OUTPUT = "output";
    public static final String OPT_CAFFE_PROCESSOR_CONNECTION = "connection";

    public static String makeOption(String opt, String val) {
        return "--" + opt + " " + val;
    }

    public static String makeOption(String opt, boolean val) {
        return "--" + opt + " " + val;
    }

    public static String makeOption(String opt, int val) {
        return "--" + opt + " " + val;
    }

}
