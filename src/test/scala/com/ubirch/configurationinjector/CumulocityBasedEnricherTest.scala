package com.ubirch.configurationinjector

import java.util.UUID

import c8y.Hardware
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.protocol.ProtocolMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{FlatSpec, Matchers}

class CumulocityBasedEnricherTest extends FlatSpec with Matchers {
  "CumulocityBasedEnricher" should "inject data from cumulocity to ubirch packet envelopes" ignore {
    val record = new ConsumerRecord[String, MessageEnvelope]("foo", 0, 0, "bar",
      MessageEnvelope(new ProtocolMessage(28, UUID.fromString("957bdffc-1a62-11e9-92bb-c83ea7010f86"), 0, null)))

    val newRecord = CumulocityBasedEnricher.enrich(record)

    import CumulocityBasedEnricher.cumulocityFormats
    val _ = newRecord.value().getContext[Hardware]("hardwareInfo")
  }
}
