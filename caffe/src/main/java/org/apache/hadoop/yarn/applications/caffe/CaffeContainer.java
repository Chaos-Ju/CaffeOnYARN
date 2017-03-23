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

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.URL;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class CaffeContainer {
    private static final Log LOG = LogFactory.getLog(CaffeContainer.class);

    private ApplicationMaster appMaster;
    public static final String SERVER_JAR_PATH = "caffe.jar";

    public CaffeContainer(ApplicationMaster am) {
        appMaster = am;
    }

    public void addToLocalResources(FileSystem fs, Path dst, String fileDstPath, Map<String, LocalResource> localResources) throws IOException {
        FileStatus scFileStatus = fs.getFileStatus(dst);
        LOG.info("Path " + dst.toString() + "->" + " " + fileDstPath);
        LocalResource scRsrc =
                LocalResource.newInstance(
                        URL.fromURI(dst.toUri()),
                        LocalResourceType.FILE, LocalResourceVisibility.APPLICATION,
                        scFileStatus.getLen(), scFileStatus.getModificationTime());
        localResources.put(fileDstPath, scRsrc);
    }

    public void addToLocalResources(FileSystem fs, String srcFilePath, String fileDstPath, Map<String, LocalResource> localResources) throws IOException {

        Path path = new Path(srcFilePath);
        addToLocalResources(fs, path, fileDstPath, localResources);
    }

    public void setNativePath(Map<String, String> env) {
        env.put("LD_LIBRARY_PATH", "/root/CaffeOnSpark/caffe-public/distribute/lib:/root/CaffeOnSpark/caffe-distri/distribute/lib");
    }

    public Map<String, String> setJavaEnv(Configuration conf, String caffeProcessorJar) {
        // Set the java environment
        Map<String, String> env = new HashMap<String, String>();

        // Add TFServer.jar location to classpath
        StringBuilder classPathEnv = new StringBuilder(ApplicationConstants.Environment.CLASSPATH.$$())
                .append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./*");

        // Add hadoop's jar location to classpath
        for (String c : conf.getStrings(
                YarnConfiguration.YARN_APPLICATION_CLASSPATH,
                YarnConfiguration.DEFAULT_YARN_CROSS_PLATFORM_APPLICATION_CLASSPATH)) {
            classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR);
            classPathEnv.append(c.trim());
        }
        classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR).append("./log4j.properties");

        // add the runtime classpath needed for tests to work
        if (conf.getBoolean(YarnConfiguration.IS_MINI_YARN_CLUSTER, false)) {
            classPathEnv.append(':');
            classPathEnv.append(System.getProperty("java.class.path"));
        }

        if (caffeProcessorJar != null) {
            classPathEnv.append(ApplicationConstants.CLASS_PATH_SEPARATOR);
            classPathEnv.append(caffeProcessorJar);
        }
        env.put("CLASSPATH", classPathEnv.toString());
        return env;
    }

    public String makeCommands(long containerMemory, String clusterSpec, int taskIndex, boolean train, String solver, boolean feature,
                                      String label, String model, String output, int connection) {
        // Set the necessary command to execute on the allocated container
        Vector<CharSequence> vargs = new Vector<CharSequence>(30);
        vargs.add(ApplicationConstants.Environment.JAVA_HOME.$$() + "/bin/java");
        vargs.add("-Xmx" + containerMemory + "m");
        String containerClassName = CaffeServer.class.getName();
        vargs.add(containerClassName);
        vargs.add("--" + CaffeServer.OPT_CS + " " + clusterSpec);
        vargs.add("--" + CaffeServer.OPT_TI + " " + taskIndex);
        vargs.add("--" + CaffeServer.OPT_SOLVER + " " + solver);
        vargs.add("--" + CaffeServer.OPT_TRAIN + " " + train);
        vargs.add("--" + CaffeServer.OPT_FEATURES + " " + feature);
        vargs.add("--" + CaffeServer.OPT_LABEL + " " + label);
        vargs.add("--" + CaffeServer.OPT_MODEL + " " + model);
        vargs.add("--" + CaffeServer.OPT_OUTPUT + " " + output);
        vargs.add("--" + CaffeServer.OPT_CONNECTION + " " + connection);

        vargs.add("1>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/Caffe.stdout");
        vargs.add("2>" + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/Caffe.stderr");

        // Get final commmand
        StringBuilder commands = new StringBuilder();
        for (CharSequence str : vargs) {
            commands.append(str).append(" ");
        }

        String command = commands.toString();

        return command;
    }

}
