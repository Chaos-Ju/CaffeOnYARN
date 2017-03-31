// Copyright 2016 Yahoo Inc.
// Licensed under the terms of the Apache 2.0 license.
// Please see LICENSE file in the project root for terms.
package com.yahoo.ml.caffe

//import org.apache.spark.rdd.RDD
//import org.apache.spark.SparkContext
//import org.apache.spark.storage.StorageLevel
import org.fusesource.lmdbjni.Transaction

import scala.collection.mutable.{ArrayBuffer, Map}

/**
 * LMDB is a built-in data source class for LMDB data source.
 * You could use this class for your LMDB data sources.
 *
 * @param conf CaffeSpark configuration
 * @param layerId the layer index in the network protocol file
 * @param isTrain
 */
class LMDB(conf: Config, layerId: Int, isTrain: Boolean) extends ImageDataSource(conf, layerId, isTrain) {

  override def read(): Array[Iterator[(String, String, Int, Int, Int, Boolean, Array[Byte])]] = {
    sourceFilePath = "file:/root/CaffeOnSpark/data/mnist_train_lmdb"
    var lmdbRDDRecords = new LmdbRDD(sourceFilePath, conf.lmdb_partitions)
    val patitons = lmdbRDDRecords.getPartitions
    val iteratorArray = new Array[Iterator[(String, String, Int, Int, Int, Boolean, Array[Byte])]](patitons.length)
    for(i <- 0 until patitons.length)
      iteratorArray(i) = lmdbRDDRecords.compute(patitons(i))
    iteratorArray
  }

  private def isNotDummy(item : (String, String, Int, Int, Int, Boolean, Array[Byte])): Boolean = {
    item._7 != null
  }
}
