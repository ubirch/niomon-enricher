package com.ubirch.configurationinjector

import com.ubirch.kafka._
import com.ubirch.niomon.base.NioMicroservice
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

class ConfigurationInjectorMicroservice(enricherFactory: NioMicroservice.Context => Enricher)
  extends NioMicroservice[MessageEnvelope, MessageEnvelope]("configuration-injector") {

  val enricher: Enricher = enricherFactory(context)

  override def processRecord(input: ConsumerRecord[String, MessageEnvelope]): ProducerRecord[String, MessageEnvelope] = {
    enricher.enrich(input).toProducerRecord(onlyOutputTopic)
  }
}
