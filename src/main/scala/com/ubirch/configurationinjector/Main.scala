package com.ubirch.configurationinjector

import com.ubirch.niomon.base.{NioMicroserviceLive}

object Main {
  def main(args: Array[String]): Unit = {
    val _ = NioMicroserviceLive("configuration-injector", ConfigurationInjectorLogic(CumulocityBasedEnricher)).runUntilDoneAndShutdownProcess
  }
}
