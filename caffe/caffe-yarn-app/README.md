CaffeOnYARN
======================
CaffeOnYARN is a YARN application to enable an easy way for end user to run Caffe application.

Note that current project is a prototype with limitation and is still under development

## Quick Start Guide
### Set up
1. Git clone ..
2. Build [CaffeOnSpark](https://github.com/yahoo/CaffeOnSpark/wiki/GetStarted_yarn).
3. Compile CaffeOnYARN

   ```sh
   cd <path_to_caffe>
   mvn clean install -DskipTests
   ```

### Run  
Run your Caffe application.

   ```sh
   cd bin
   ydl-caffe -jar <path_to_caffe-on-yarn-with-dependency_jar> \
        -conf your_solver_protoxt.prototxt \
        -feature accuracy,loss \ 
        -label label \
        -model hdfs:///mnist.model \
        -output hdfs:///mnist_features_result \
        -num 3 
   ```