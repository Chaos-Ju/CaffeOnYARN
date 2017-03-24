# Caffe On YARN

Caffe on YARN is a project to support running Caffe on YARN, based on [CaffeOnSpark](https://github.com/yahoo/CaffeOnSpark) from yahoo to rebase on YARN by removing Spark dependency. Note that current project is a prototype with limitation and is still under development.

## Architecture
<p align="center">
<img src=https://cloud.githubusercontent.com/assets/9171954/24284565/e02b511c-10a6-11e7-8af3-5888869ea692.png>
</p>
<p align="center">
Figure1. CaffeOnYARN Architecture
</p>

## Quick Start Guide
### Set up
1. Git clone ..
2. Set environment variables

   ```sh
   export CAFFE_ON_YARN=$(pwd)/CaffeOnYARN
   export LD_LIBRARY_PATH=${CAFFE_ON_YARN}/jni_so
   ```

3. Compile CaffeOnYARN

   ```sh
   cd <path_to_caffe>
   mvn clean install
   ```

### Run  
Run your Caffe application.

   ```sh
   cd bin
   ydl-cf -jar <path_to_caffe-with-dependency_jar> \
             -conf <your_solver_protoxt> \
             -model <model_output_hdfs_path> \
             -num <container_num>
   ```