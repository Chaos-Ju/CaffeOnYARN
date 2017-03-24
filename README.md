# CaffeOnYARN

CaffeOnYARN is a project to support running Caffe on YARN, based on [CaffeOnSpark](https://github.com/yahoo/CaffeOnSpark) from yahoo to rebase on YARN by removing Spark dependency. Note that current project is a prototype with limitation and is still under development.

## Quick Start Guide
### Set up
1. Git clone ..
2. Build [CaffeOnSpark](https://github.com/yahoo/CaffeOnSpark/wiki/GetStarted_yarn).
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
             -conf <your_solver_protoxt>' \
             -model <model_output_hdfs_path>' \
             -num <container_num>'
   ```