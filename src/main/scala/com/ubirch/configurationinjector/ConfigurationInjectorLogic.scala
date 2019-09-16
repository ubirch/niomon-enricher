package com.ubirch.configurationinjector

import com.ubirch.kafka._
import com.ubirch.niomon.base.{NioMicroservice, NioMicroserviceLogic}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

class ConfigurationInjectorLogic(
  enricherFactory: NioMicroservice.Context => Enricher,
  runtime: NioMicroservice[MessageEnvelope, MessageEnvelope]
) extends NioMicroserviceLogic[MessageEnvelope, MessageEnvelope](runtime) {

  val enricher: Enricher = enricherFactory(context)

  override def processRecord(input: ConsumerRecord[String, MessageEnvelope]): ProducerRecord[String, MessageEnvelope] = {
    enricher.enrich(input).toProducerRecord(onlyOutputTopic) // TODO: drop auth headers
  }
}

object ConfigurationInjectorLogic {
  def apply(enricherFactory: NioMicroservice.Context => Enricher)
    (runtime: NioMicroservice[MessageEnvelope, MessageEnvelope]) =
    new ConfigurationInjectorLogic(enricherFactory, runtime)
}
