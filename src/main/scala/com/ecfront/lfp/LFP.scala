package com.ecfront.lfp

import java.io.File
import java.util.concurrent.CountDownLatch

import akka.actor.{ActorSystem, Props}
import com.typesafe.scalalogging.slf4j.LazyLogging

/**
 * 程序入口类
 * @param path 文件路径
 * @param processFun 自定义的处理函数，输入为行列表
 * @param chunkSize 每次抓取文件大小（bytes）
 */
class LFP(path: String, processFun: (Array[String] => Unit), chunkSize: Int = 300000) extends LazyLogging {

  private val startTime = System.currentTimeMillis()

  logger.debug("Started LFP,path: " + path + ",chunkSize:" + chunkSize + ",time:" + startTime)

  private val file = new File(path)
  private val count = (file.length() + chunkSize - 1) / chunkSize - 1
  //计数器，用于统计是否完成
  private val counter = new CountDownLatch(count.intValue() + 1)
  logger.debug("Counter total :" + counter.getCount)

  private val router = LFP.system.actorOf(Props(new LineCollector(LFP.system.actorOf(Props(new LineProcessor(processFun, counter))))))

  for (i <- 0 until count.toInt) {
    val start = i * chunkSize
    val end = chunkSize + start
    router !(path, start, end)
  }

  private val remaining = chunkSize * count.toInt
  router !(path, remaining, file.length().toInt)

  logger.debug("Wait finish ...")
  counter.await()

  private val endTime = System.currentTimeMillis()
  logger.debug("Completed at ==>" + endTime)
  logger.debug("Used ==>" + (endTime - startTime))

}

object LFP extends LazyLogging {

  logger.info("Init Large File Processor.")

  val system = ActorSystem("lfp_system")

  //threadNumber 工作线程数
  def setThread(threadNumber: Int): Unit = {
    LineProcessor.init(threadNumber)
  }

  def setAsync() = {
    LineProcessor.async = true
  }

  def apply(path: String, processFun: (Array[String] => Unit)) = {
    new LFP(path, processFun)
  }

  def apply(path: String, processFun: (Array[String] => Unit), chunkSize: Int) = {
    new LFP(path, processFun, chunkSize)
  }

}
