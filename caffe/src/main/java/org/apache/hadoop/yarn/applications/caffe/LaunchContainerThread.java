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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.yarn.api.records.*;

import java.io.IOException;
import java.util.*;

public class LaunchContainerThread implements Runnable {

    private static final Log LOG = LogFactory.getLog(LaunchContainerThread.class);

    private Container container;
    private String caffeProcessorJar;
    private long containerMemory = 100;

    private String solver;
    private boolean train;
    private boolean feature;
    private String label;
    private String model;
    private String output;
    private int connection;

    private String[] addresses;

    // Container retry options
    private ContainerRetryPolicy containerRetryPolicy =
            ContainerRetryPolicy.NEVER_RETRY;
    private Set<Integer> containerRetryErrorCodes = null;
    private int containerMaxRetries = 0;
    private int containrRetryInterval = 0;

    private ApplicationMaster appMaster;

    private CaffeServerAddress serverAddress = null;

    public void setCaffeProcessorJar(String caffeProcessorJar) {
        this.caffeProcessorJar = caffeProcessorJar;
    }

    public void setContainerMemory(long containerMemory) {
        this.containerMemory = containerMemory;
    }

    public void setContainerRetryPolicy(ContainerRetryPolicy containerRetryPolicy) {
        this.containerRetryPolicy = containerRetryPolicy;
    }

    public void setContainerRetryErrorCodes(Set<Integer> containerRetryErrorCodes) {
        this.containerRetryErrorCodes = containerRetryErrorCodes;
    }

    public void setContainerMaxRetries(int containerMaxRetries) {
        this.containerMaxRetries = containerMaxRetries;
    }

    public void setContainrRetryInterval(int containrRetryInterval) {
        this.containrRetryInterval = containrRetryInterval;
    }

    private LaunchContainerThread(Container container, ApplicationMaster appMaster) {
        this.container = container;
        this.appMaster = appMaster;
    }

    public LaunchContainerThread(Container container, boolean train, String solver, boolean feature,
                                 String label, String model, String output, int connection, String[] addresses,
                                 ApplicationMaster appMaster, CaffeServerAddress serverAddress) {
        this(container, appMaster);
        this.serverAddress = serverAddress;
        this.train = train;
        this.solver = solver;
        this.feature = feature;
        this.label = label;
        this.model = model;
        this.output = output;
        this.connection = connection;
        this.addresses = addresses;
        if (this.serverAddress == null) {
            LOG.info("server address is null");
        }
    }

    @Override
    /**
     * Connects to CM, sets up container launch context
     * for shell command and eventually dispatches the container
     * start request to the CM.
     */
    public void run() {
        LOG.info("Setting up container launch container for containerid="
                + container.getId());

        FileSystem fs = null;
        try {
            fs = FileSystem.get(appMaster.getConfiguration());
        } catch (IOException e) {
            e.printStackTrace();
        }

        CaffeContainer caffeContainer = new CaffeContainer(appMaster);

        Map<String, String> env = caffeContainer.setJavaEnv(appMaster.getConfiguration(), null);
        caffeContainer.setNativePath(env);

        Map<String, LocalResource> localResources = new HashMap<String, LocalResource>();

        try {
            caffeContainer.addToLocalResources(fs, caffeProcessorJar, CaffeContainer.SERVER_JAR_PATH, localResources);
        } catch (IOException e) {
            e.printStackTrace();
        }

        LOG.info("cluster: " + this.serverAddress.getClusterSpec().toString());
        ClusterSpec cs = this.serverAddress.getClusterSpec();

        String command = null;
        try {
            command = caffeContainer.makeCommands(containerMemory,
                    cs.getBase64EncodedJsonString(),
                    this.serverAddress.getTaskIndex(),
                    this.train,
                    this.solver,
                    this.feature,
                    this.label,
                    this.model,
                    this.output,
                    this.connection,
                    this.addresses);
        } catch (JsonProcessingException e) {
            LOG.info("cluster spec cannot convert into base64 json string!");
            e.printStackTrace();
        } catch (ClusterSpecException e) {
            e.printStackTrace();
        }

        List<String> commands = new ArrayList<>();
        commands.add(command);
        if (serverAddress != null) {
            LOG.info(serverAddress.getAddress() + ":" + serverAddress.getPort());
        }

        ContainerRetryContext containerRetryContext =
                ContainerRetryContext.newInstance(
                        containerRetryPolicy, containerRetryErrorCodes,
                        containerMaxRetries, containrRetryInterval);
        for (String cmd : commands) {
            LOG.info("Container " + container.getId() + " command: " + cmd.toString());
        }
        ContainerLaunchContext ctx = ContainerLaunchContext.newInstance(
                localResources, env, commands, null, appMaster.getAllTokens().duplicate(),
                null, containerRetryContext);
        appMaster.addContainer(container);
        appMaster.getNMClientAsync().startContainerAsync(container, ctx);
    }

}