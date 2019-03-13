package com.ubirch.configurationinjector

object Main {
  def main(args: Array[String]): Unit = {
    new ConfigurationInjectorMicroservice(CumulocityBasedEnricher).run
  }
}
