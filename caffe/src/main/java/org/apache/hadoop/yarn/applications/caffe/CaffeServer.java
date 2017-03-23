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

import com.yahoo.ml.caffe.Config;
import com.yahoo.ml.caffe.DataSource;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import scala.Tuple7;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CaffeServer {
    private static final Log LOG = LogFactory.getLog(CaffeServer.class);

    public static final String OPT_CS = "cs";
    public static final String OPT_TI = "ti";
    public static final String OPT_SOLVER = "conf";
    public static final String OPT_TRAIN = "train";
    public static final String OPT_FEATURES = "feature";
    public static final String OPT_LABEL = "label";
    public static final String OPT_MODEL = "model";
    public static final String OPT_OUTPUT = "output";
    public static final String OPT_CONNECTION = "connection";

    public String clusterSpecString = null;
    public Map<String, List<String>> cluster = null;
    private int taskIndex = -1;

    public String solver;
    public boolean train;
    public boolean feature;
    public String label;
    public String model;
    public String output;
    public int connection;

    com.yahoo.ml.caffe.Config config;
    com.yahoo.ml.caffe.DataSource<scala.Tuple7, scala.Tuple2> sourceTrain;

    // Command line options
    private Options opts;

    public static void main(String[] args) {
        LOG.info("start container");
        CaffeServer server = new CaffeServer();
        try {
            try {
                if (!server.init(args)) {
                    LOG.info("init failed!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (ParseException e) {
            LOG.info("parse failed");
            e.printStackTrace();
        }
        server.startCaffeServer();
    }


    public CaffeServer() {
        opts = new Options();
        opts.addOption(OPT_CS, true, "caffe server cluster spec");
        opts.addOption(OPT_TI, true, "caffe task index");
        opts.addOption(OPT_SOLVER, true,
                "solver_configuration");
        opts.addOption(OPT_TRAIN, true,
                "training_mode");
        opts.addOption(OPT_FEATURES, true,
                "name_of_output_blobs");
        opts.addOption(OPT_LABEL, true,
                "name of label blobs to be included in features");
        opts.addOption(OPT_MODEL, true,
                "model path");
        opts.addOption(OPT_OUTPUT, true,
                "output path");
        opts.addOption(OPT_CONNECTION, true,
                "connect mode");
    }

    public boolean init(String[] args) throws ParseException, IOException {

        CommandLine cliParser = new GnuParser().parse(opts, args);

        if (args.length == 0) {
            throw new IllegalArgumentException("No args specified for caffe server to initialize");
        }

        if (!cliParser.hasOption(OPT_CS) || !cliParser.hasOption(OPT_TI)) {
            LOG.error("invalid args for caffe server!");
            return false;
        }

        clusterSpecString = ClusterSpec.decodeJsonString(cliParser.getOptionValue(OPT_CS));
        taskIndex = Integer.parseInt(cliParser.getOptionValue(OPT_TI));
        solver = cliParser.getOptionValue(OPT_SOLVER, "");
        train = cliParser.hasOption(OPT_TRAIN);
        feature = cliParser.hasOption(OPT_FEATURES);
        label = cliParser.getOptionValue(OPT_LABEL, "");
        model = cliParser.getOptionValue(OPT_MODEL, "");
        output = cliParser.getOptionValue(OPT_OUTPUT, "");
        connection = Integer.parseInt(cliParser.getOptionValue(OPT_CONNECTION, "2"));
        cluster = ClusterSpec.toClusterMapFromJsonString(clusterSpecString);

        config = new Config(args);
        sourceTrain = DataSource.getSource(config, true);

        return true;
    }

    public void train() {

       String[] addresses = new String[cluster.get("processor").size()];
       cluster.get("processor").toArray(addresses);

       DataSource<scala.Tuple7, scala.Tuple2> source[]=new DataSource[1];
       source[0] = sourceTrain;

       com.yahoo.ml.caffe.CaffeProcessor caffeProcessor = com.yahoo.ml.caffe.CaffeProcessor.instance(source, taskIndex);
       caffeProcessor.start(addresses);

       caffeProcessor.sync();

       scala.collection.Iterator<Tuple7<String, String, Object, Object, Object, Object, byte[]>> data[];
       data = sourceTrain.read();

       for(int i = 0; i < data.length; i++){
           scala.collection.Iterator<Tuple7<String, String, java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object, byte[]>> iter = data[i];
           while (iter.hasNext()) {
               caffeProcessor.feedQueue(0, iter.next());
           }
       }

       caffeProcessor.stop();

       LOG.info("train success");

    }

    public void startCaffeServer() {
        LOG.info("Launch a new caffeProcessor: " + taskIndex);
        train();
        LOG.info("caffeProcessor: " + taskIndex + " stopped!");
    }
}
