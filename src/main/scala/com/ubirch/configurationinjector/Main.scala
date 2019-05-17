package com.ubirch.configurationinjector

object Main {
  def main(args: Array[String]): Unit = {
    val _ = new ConfigurationInjectorMicroservice(CumulocityBasedEnricher).runUntilDoneAndShutdownProcess
  }
}
