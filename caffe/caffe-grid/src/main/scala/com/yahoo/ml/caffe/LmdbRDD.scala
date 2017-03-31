// Copyright 2016 Yahoo Inc.
// Licensed under the terms of the Apache 2.0 license.
// Please see LICENSE file in the project root for terms.
package com.yahoo.ml.caffe

import java.io.{File, FilenameFilter}
import java.util.concurrent.ConcurrentHashMap

import caffe.Caffe.Datum
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.conf.Configuration;

//import org.apache.spark.rdd.{MapPartitionsRDD, RDD}
//import org.apache.spark.{Partition, SparkContext, SparkFiles, TaskContext}
import org.fusesource.lmdbjni.{Database, Entry, Env, Transaction}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

/**
 * Each LMDB RDD partition has a start key. from which we will enumerate a number of entries
 * @param idx partition ID
 * @param startKey start key
 * @param size # of entries in this partition
 */
private[caffe] class LmdbPartition(idx: Int, val startKey: Array[Byte], val size: Int) extends Serializable {
  def index: Int = idx
}

/**
 * LmdbRDD is a custom RDD for accessing LMDB databases using a specified # of partitions.
 *
 * @param sc spark context
 * @param lmdb_path URI of LMDB databases
 * @param numPartitions # of the desired partitions.
 */

class LmdbRDD(val lmdb_path: String, val numPartitions: Int)
  extends Serializable {
  @transient var env: Env = null
  @transient var db: Database = null

  @transient val log: Logger = LoggerFactory.getLogger(this.getClass)

 def getPartitions: Array[LmdbPartition] = {

    //make sourceFilePath downloaded to all nodes
    //LmdbRDD.DistributeLMDBFilesIfNeeded("file:/root/CaffeOnSpark/data/mnist_train_lmdb/data.mdb")

    openDB()

    //part_size: # of keys to be included in each partitions
    val size: Long = db.stat().ms_entries
    val part_size: Int = Math.ceil(size.toDouble / numPartitions.toDouble).toInt

    var is_done = false
    var next: Entry = null
    val partitions = new Array[LmdbPartition](numPartitions)
    //last key in previous partition
    var start_key: Array[Byte] = null

    var part_index: Int = 0
    partitions(part_index) = new LmdbPartition(part_index, null, part_size)

    while (is_done == false && (part_index+1) < numPartitions) {
      val txn = env.createReadTransaction()

      try {
        val it = if (part_index == 0) db.iterate(txn)
        else db.seek(txn, start_key)

        //skip (part_size) entries
        var pos_in_partition: Int = 0
        while (it.hasNext && (pos_in_partition < part_size)) {
          next = it.next()
          pos_in_partition = pos_in_partition + 1
        }

        //start key for next partition
        if (it.hasNext) {
          start_key = it.next().getKey()
          part_index = part_index + 1
          partitions(part_index) = new LmdbPartition(part_index, start_key, part_size)
        } else {
          is_done = true
        }
      } catch {
        case e: Exception => {
          log.warn(e.toString, e)
          is_done = true
        }
      } finally {
        commit(txn)
      }
    }
    closeDB()

    log.info((part_index+1) + " LMDB RDD partitions")
    partitions
  }


  def compute(thePart: LmdbPartition):
  Iterator[(String, String, Int, Int, Int, Boolean, Array[Byte])] = {
    new Iterator[(String, String, Int, Int, Int, Boolean, Array[Byte])] {
      log.info("Processing partition " + thePart.index)
      openDB()

      val part = thePart.asInstanceOf[LmdbPartition]
      val txn: Transaction = env.createReadTransaction()
      var pos_in_partition: Int = 0
      var it = if (part != null && txn != null) {
        if (part.startKey == null) db.iterate(txn) else db.seek(txn, part.startKey)
      }
      else {
        closeDB()
        null
      }

      def hasNext(): Boolean = {
        if (it == null) return false

        val res = it.hasNext
        if (!res || (pos_in_partition == part.size)) {
          commit(txn)
          closeDB()
          it = null
          log.info("Completed partition " + thePart.index)
        }
        res
      }

      def next(): (String, String, Int, Int, Int, Boolean, Array[Byte]) = {
        if (it == null)
          ("", "", 0, 0, 0, false, null)
        else {
          val next = it.next()
          pos_in_partition = pos_in_partition + 1

          val id: String = new String(next.getKey())

          val datum_bdr = Datum.newBuilder()
          datum_bdr.mergeFrom(next.getValue())
          val datum = datum_bdr.build()

          val label: String = datum.getLabel().toString()
          //log.debug("ID:" + id + " label:" + label)

          val channels: Int = datum.getChannels()
          val height: Int = datum.getHeight()
          val width: Int = datum.getWidth()
          val encoded: Boolean = datum.getEncoded()
          val matData: Array[Byte] =
            if (encoded) datum.getData().toByteArray()
            else LmdbRDD.LMDBdata2Matdata(channels, height * width, datum.getData().toByteArray())

          (id, label, channels, height, width, encoded, matData)
        }
      }
    }
  }

  private def commit(txn: Transaction): Unit = {
    try {
      if (txn != null)
        txn.commit()
    } catch {
      case e: Exception => log.warn("Exception commit transaction", e)
    }
  }

  private def localLMDBFile(): String = {
    /*
    synchronization to avoid potential file corruption
     */
    synchronized {
      //local file name
      val folder: Path = new Path(lmdb_path)
      val local_lmdb_folder = folder.toString.substring(FSUtils.localfsPrefix.length)

      //make sure that all mdb files are writable
      val db_files = new File(local_lmdb_folder).listFiles(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean =
          name.toLowerCase().endsWith(".mdb")
      })
      for (db_file <- db_files)
        db_file.setWritable(true)

      //return
      local_lmdb_folder
    }
  }

  /*
  open Database if needed
 */
  private def openDB(): Unit = {
    //load lmdbjni
    LmdbRDD.loadLibrary()

    if (env == null)
      env = new Env(localLMDBFile())

    if (db == null)
      db = env.openDatabase(null, 0)
  }

  /*
  close Database
   */
  private def closeDB(): Unit = {
    try {
      if (db != null) {
        db.close()
        db = null
      }

      if (env != null) {
        env.close()
        env = null
      }
    } catch {
      case e: Exception => log.warn("Exception closing database", e)
    }
  }

  /*
   * Database will be closed by GC.
   */
  override protected def finalize(): Unit = {
    closeDB()
  }
}

private[caffe] object LmdbRDD {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  private var libLoaded: Boolean = false
  private val lmdb_paths = new ConcurrentHashMap[String,Int]()

  //load lmdbjni
  private def loadLibrary(): Unit = {
    synchronized {
      if (!libLoaded) {
        log.debug("java.library.path:" + System.getProperty("java.library.path"))
        System.loadLibrary("lmdbjni")
        log.debug("System load liblmdbjni.so successed")
        libLoaded = true
      }
    }
  }

  //make sourceFilePath downloaded to all nodes
  private def DistributeLMDBFilesIfNeeded(lmdb_path: String) : Unit = {
    log.info("startsWith " + lmdb_path.startsWith(FSUtils.localfsPrefix))
    val conf = new Configuration()
    val fs: FileSystem = FileSystem.get(conf)
    val local: Path = new Path(lmdb_path)
    val dst: Path = new Path(fs.getHomeDirectory, "lmdb file")
    fs.copyFromLocalFile(local, dst)
  }

  /**
   * Pixel data reordered from LMDB format to cv:Mat format.
   * LMDB format is (channel, height, width), and data are (R, R,..., G, G, ..., B, B ...)
   * Mat format is (height, width, channel), and data are (R, G, B, R, G, B, ...)
   *
   * @param channels
   * @param dimension_size
   * @param data
   * @return
   */
  private[caffe] def LMDBdata2Matdata(channels: Int, dimension_size: Int, data: Array[Byte]): Array[Byte] = {
    if (channels == 1) data
    else {
      val data_clone = data.clone()

      for (p <- 0 until dimension_size)
        for (c <- 0 until channels)
          data_clone(p * channels + c) = data(p + c * dimension_size)

      data_clone
    }
  }
}
