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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.util.*;

public class ClusterSpec {

    private static final Log LOG = LogFactory.getLog(ClusterSpec.class);
    private Map<String, CaffeServerAddress> servers = null;
    private CaffeServerAddress caffeMasterNode = null;
    private int serverPortNext = PORT_FLOOR;

    private static final int PORT_FLOOR = 20000;
    private static final int PORT_CEILING = 25000;

    public static final String Processors = "processor";

    private int numTotalProcessorServers = 0;

    public void setNumTotalProcessorServers(int numTotalProcessorServers) {
        this.numTotalProcessorServers = numTotalProcessorServers;
    }

    public static ClusterSpec makeClusterSpec(int numTotalProcessorServers) {
        return new ClusterSpec(numTotalProcessorServers);
    }

    private ClusterSpec(int numTotalProcessorServers) {
        this.setNumTotalProcessorServers(numTotalProcessorServers);
        servers = new HashMap<>();
        serverPortNext = PORT_FLOOR + ((int) (Math.random() * (PORT_CEILING - PORT_FLOOR)) + 1);
    }

    private int nextRandomPort() {
        int port = serverPortNext;
        serverPortNext = serverPortNext + 3;
        return port;
    }

    public void addServerSpec(String containerId, String hostName) {

        CaffeServerAddress server = new CaffeServerAddress(this, hostName, nextRandomPort(), this.servers.size());
        if (caffeMasterNode == null) {
            caffeMasterNode = server;
        }
        this.servers.put(containerId, server);
    }

    public CaffeServerAddress getMasterNode() {
        return caffeMasterNode;
    }

    public CaffeServerAddress getServerAddress(String containerId) {
        CaffeServerAddress server = this.servers.get(containerId);
        if (server == null) {
            LOG.info(containerId + " is not a caffe processor");
            server = this.servers.get(containerId);
        }

        return server;
    }

    private boolean checkAllocationCompleted() {
        return this.servers.size() == this.numTotalProcessorServers;
    }

    @Override
    public String toString() {
        String server_array = "";
        for (CaffeServerAddress server : servers.values()) {
            server_array += server.getAddress() + ":" + server.getPort() + ",";
        }
        return server_array;
    }

    public String getJsonString() throws JsonProcessingException, ClusterSpecException {
        if (!checkAllocationCompleted()) {
            throw new ClusterSpecException("not allocation completed");
        }
        Map<String, List<String>> cluster = new HashMap<>();

        if (!this.servers.isEmpty()) {
            List<String> servers = new ArrayList<String>();
            for (CaffeServerAddress s : this.servers.values()) {
                String addr = "" + s.getAddress() + ":" + s.getPort();
                servers.add(addr);
            }
            cluster.put(Processors, servers);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        String json = null;
        json = objectMapper.writeValueAsString(cluster);
        return json;
    }

    public String getBase64EncodedJsonString() throws JsonProcessingException, ClusterSpecException {
        byte[] data = getJsonString().getBytes();
        Base64 encoder = new Base64(0, null, true);
        return encoder.encodeToString(data);
    }

    public static String decodeJsonString(String base64String) {
        Base64 decoder = new Base64(0, null, true);
        byte[] data = decoder.decode(base64String);
        return new String(data);
    }

    public static Map<String, List<String>> toClusterMapFromJsonString(String clusterString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, List<String>> cluster = null;
        cluster = objectMapper.readValue(clusterString, Map.class);

        return cluster;
    }

}