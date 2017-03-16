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

package org.apache.hadoop.yarn.applications.caffe;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.URL;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;


public class CaffeAmContainer {
    private static final Log LOG = LogFactory.getLog(CaffeAmContainer.class);
    public static final String APPMASTER_JAR_PATH = "AppMaster.jar";
    private Client client;


    public CaffeAmContainer(Client client) {
        this.client = client;
    }

    public void addToLocalResources(FileSystem fs, Path dst, String fileDstPath, Map<String, LocalResource> localResources) throws IOException {
        FileStatus scFileStatus = fs.getFileStatus(dst);
        LocalResource scRsrc =
                LocalResource.newInstance(
                        URL.fromURI(dst.toUri()),
                        LocalResourceType.FILE, LocalResourceVisibility.APPLICATION,
                        scFileStatus.getLen(), scFileStatus.getModificationTime());
        localResources.put(fileDstPath, scRsrc);
    }

    public Map<String, String> setJavaEnv(Configuration conf) {
        Map<String, String> env = new HashMap<String, String>();

        // Add AppMaster.jar location to classpath
        // At some point we should not be required to add
        // the hadoop specific classpaths to the env.
        // It should be provided out of the box.
        // For now setting all required classpaths including
        // the classpath to "." for the application jar
        StringBuilder classPathEnv = new StringBuilder(ApplicationConstants.Environment.CLASSPATH.$$())
                .append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./*");
        for (String c : conf.getStrings(
                YarnConfiguration.YARN_APPLICATION_CLASSPATH,
                YarnConfiguration.DEFAULT_YARN_CROSS_PLATFORM_APPLICATION_CLASSPATH)) {
            classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR);
            classPathEnv.append(c.trim());
        }
        classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR).append(
                "./log4j.properties");

        // add the runtime classpath needed for tests to work
        if (conf.getBoolean(YarnConfiguration.IS_MINI_YARN_CLUSTER, false)) {
            classPathEnv.append(':');
            classPathEnv.append(System.getProperty("java.class.path"));
        }

        env.put("CLASSPATH", classPathEnv.toString());
        return env;
    }


    public StringBuilder makeCommands(long amMemory, String appMasterMainClass, int containerMemory, int containerVirtualCores,
                                      int workerNumContainers, String jarDfsPath, Vector<CharSequence> containerRetryOptions,
                                      boolean train, String solver, boolean feature,
                                      String label, String model, String output, int connection) {
        // Set the necessary command to execute the application master
        Vector<CharSequence> vargs = new Vector<CharSequence>(30);

        // Set java executable command
        LOG.info("Setting up app master command");
        vargs.add(ApplicationConstants.Environment.JAVA_HOME.$$() + "/bin/java");
        // Set Xmx based on am memory size
        vargs.add("-Xmx" + amMemory + "m");
        // Set class name
        vargs.add(appMasterMainClass);
        // Set params for Application Master
        vargs.add("--container_memory " + String.valueOf(containerMemory));
        vargs.add("--container_vcores " + String.valueOf(containerVirtualCores));
        vargs.add(CaffeApplication.makeOption(CaffeApplication.OPT_CAFFE_PROCESSOR_NUM, String.valueOf(workerNumContainers)));
        vargs.add("--" + CaffeApplication.OPT_CAFFE_PROCESSOR_JAR + " " + String.valueOf(jarDfsPath));
        vargs.add(CaffeApplication.makeOption(CaffeApplication.OPT_CAFFE_PROCESSOR_TRAIN, train));
        vargs.add(CaffeApplication.makeOption(CaffeApplication.OPT_CAFFE_PROCESSOR_FEATURES, feature));
        vargs.add(CaffeApplication.makeOption(CaffeApplication.OPT_CAFFE_PROCESSOR_SOLVER, solver));
        vargs.add(CaffeApplication.makeOption(CaffeApplication.OPT_CAFFE_PROCESSOR_LABEL, label));
        vargs.add(CaffeApplication.makeOption(CaffeApplication.OPT_CAFFE_PROCESSOR_MODEL, model));
        vargs.add(CaffeApplication.makeOption(CaffeApplication.OPT_CAFFE_PROCESSOR_OUTPUT, output));
        vargs.add(CaffeApplication.makeOption(CaffeApplication.OPT_CAFFE_PROCESSOR_CONNECTION, connection));

        vargs.addAll(containerRetryOptions);

        vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stdout");
        vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/AppMaster.stderr");

        // Get final commmand
        StringBuilder command = new StringBuilder();
        for (CharSequence str : vargs) {
            command.append(str).append(" ");
        }
        return command;
    }

}
