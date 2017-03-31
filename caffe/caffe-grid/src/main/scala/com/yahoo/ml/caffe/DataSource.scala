// Copyright 2016 Yahoo Inc.
// Licensed under the terms of the Apache 2.0 license.
// Please see LICENSE file in the project root for terms.
package com.yahoo.ml.caffe

import java.util.concurrent.ArrayBlockingQueue

import caffe.Caffe._
import com.yahoo.ml.jcaffe._
import org.slf4j.{LoggerFactory, Logger}

/**
 * Base class for various data sources.
 *
 * Each subclass must have a constructor with the following signature: (conf: Config, layerId: Int, isTrain: Boolean).
 * This is required by CaffeOnSpark at startup.
 *
 * @param conf CaffeSpark configuration
 * @param layerId the layer index in the network protocol file
 * @param STOP_MARK stop mark to indicate source is exhausted
 * @tparam T1 class of entries extracted from RDD
 * @tparam T2 class of data blob in batch
 */
abstract class DataSource[T1, T2](val conf: Config, val layerId : Int, val isTrain: Boolean, val STOP_MARK: T1)
  extends Serializable {
  @transient private[caffe] var solverParameter: SolverParameter = null
  @transient private[caffe] var layerParameter: LayerParameter = null
  @transient private[caffe] var transformationParameter:TransformationParameter = null
  @transient protected var sourceQueue: ArrayBlockingQueue[T1] = null
  @transient protected var sourceFilePath : String = null
  @transient protected var batchSize_ : Int = -1
  @transient protected var solverMode: Int = -1


  def read(): Array[Iterator[(String, String, Int, Int, Int, Boolean, Array[Byte])]]

  /**
   *  initialization of a Source within a process
   *  @return true if successfully initialized
   */
  def init() : Boolean = {

    //solver parameter
    solverParameter = conf.solverParameter
    solverMode = solverParameter.getSolverMode().getNumber()

    //layer parameter
    layerParameter = conf.netParam.getLayer(layerId)

    //transformer parameter
    transformationParameter = layerParameter.getTransformParam()

    true
  }

  /**
   * set up source queue with appropriate capacity.
   * This method should be invoked after init() and before all other invocations (ex. feed queue)
   */
  private[caffe] def resetQueue(capacity_limit: Int = 0) : Unit = {
    if (sourceQueue == null || (capacity_limit >= 0 && sourceQueue.size() != capacity_limit)) {
      if (capacity_limit <= 0 || capacity_limit > 1024)
        sourceQueue = new ArrayBlockingQueue[T1](1024)
      else
        sourceQueue = new ArrayBlockingQueue[T1](capacity_limit)
    } else {
      sourceQueue.clear()
    }
  }

  /**
   * adjust batch size
   * @param size the new batch size
   */
  def setBatchSize(size: Int) : Unit = {
    batchSize_ = size
  }

  /**
   * batch size
   */
  def batchSize() : Int = batchSize_

  /**
   * make a dummy data blob to be used by Solver threads
   * @return a dummy data blob
   */
  def dummyDataHolder() : T2

  /**
   * make a dummy data blob to be used by Solver threads
   * @return a dummy data blob
   */
  def dummyDataBlobs() : Array[FloatBlob]

  /**
   * feed an sample to source queue
   * @param sample an sample to be fed
   * @return true if success, false if failed
   */
  def offer(sample: T1) : Boolean = sourceQueue.offer(sample)

  /**
   * create a batch of samples extracted from source queue
   *
   * This method is Invoked by Transformer thread.
   * You should extract samples from source queue, parse it and produce a batch.
   * @param sampleIds holder for sample Ids
   * @param data holder for data blob
   * @return true if successful
   */
  def nextBatch(sampleIds: Array[String], data: T2) : Boolean

  def useCoSDataLayer(): Boolean = false

  def getNumTops(): Int = 0

  def getTopDataType(index: Int): CoSDataParameter.DataType = null

  def getTopTransformParam(index: Int): TransformationParameter = null
}

object DataSource extends Serializable {


  def getSource[T1, T2](conf : Config, isTraining: Boolean): DataSource[T1, T2] = {
    @transient val log: Logger = LoggerFactory.getLogger(this.getClass)

    val layerId = if (isTraining) conf.train_data_layer_id else conf.test_data_layer_id
    log.info("Source data layer:"+layerId)
    val layerParameter = conf.netParam.getLayer(layerId)

    //get JVM class name
    val class_name : String = if (layerParameter.hasSourceClass()) layerParameter.getSourceClass() else null
    if (class_name == null) {
      val layerType = layerParameter.getType()
      log.error("source_class must be defined for input data layer:"+layerType)
      return null
    }

    //load JVM class
    val clz = Class.forName(class_name)
    if (clz == null) {
      log.error("failed to load class: "+class_name)
      return null
    }

    //locate a constructor of source class
    val constructor = clz.getConstructor(conf.getClass, java.lang.Integer.TYPE, java.lang.Boolean.TYPE)
    if (constructor == null) {
      log.error(class_name + " doesn't have constructor of required signature (conf: Config, layerId: Int, isTrain: Boolean)")
      System.exit(4)
    }

    val source : DataSource[T1, T2] = constructor.newInstance(conf,
      new Integer(layerId),
      new java.lang.Boolean(isTraining)).asInstanceOf[DataSource[T1, T2]]
    source.init()

    source
  }
}
