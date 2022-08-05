package com.ubirch.configurationinjector

import com.typesafe.scalalogging.LazyLogging

case class DeviceInfo(hwDeviceId: String,
                      description: String,
                      customerId: String,
                      attributes: Map[String, String])

trait UbirchEnricher extends Enricher with LazyLogging {

  def xcode(reason: Throwable): Int = reason match {
    case _: NoSuchElementException => 1000
    case _: IllegalArgumentException => 2000
    case _: RuntimeException => 4000
    case _ => 5000
  }

}
