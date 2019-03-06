package com.ubirch.configurationinjector

import com.ubirch.kafka.MessageEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord

trait Enricher {
  def enrich(record: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope]
}
