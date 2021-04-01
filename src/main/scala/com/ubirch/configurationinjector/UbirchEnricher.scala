package com.ubirch.configurationinjector

import com.typesafe.scalalogging.LazyLogging

trait UbirchEnricher extends Enricher with LazyLogging {

  case class DeviceInfo(hwDeviceId: String, description: String, customerId: String)

  def xcode(reason: Throwable): Int = reason match {
    case _: NoSuchElementException => 1000
    case _: IllegalArgumentException => 2000
    case _: RuntimeException => 4000
    case _ => 5000
  }

}
