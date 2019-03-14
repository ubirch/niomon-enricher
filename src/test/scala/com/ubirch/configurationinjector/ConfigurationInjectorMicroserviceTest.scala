package com.ubirch.configurationinjector

import java.util.UUID

import com.ubirch.kafka._
import com.ubirch.protocol.ProtocolMessage
import net.manub.embeddedkafka.EmbeddedKafka
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{FlatSpec, Matchers}

//noinspection TypeAnnotation
class ConfigurationInjectorMicroserviceTest extends FlatSpec with Matchers with EmbeddedKafka {
  implicit val stringSerializer = new StringSerializer
  implicit val stringDeserializer = new StringDeserializer
  implicit val envelopeSerializer = EnvelopeSerializer
  implicit val envelopeDeserializer = EnvelopeDeserializer

  "configuration injector microservice" should "inject configuration using given enricher" in {
    var timesEnricherInvoked = 0
    val enricher: Enricher = { record => timesEnricherInvoked += 1; record.withExtraContext("foo", 42) }

    withRunningKafka {
      val microservice = new ConfigurationInjectorMicroservice(_ => enricher)
      microservice.run

      val toSend = List(
        "key-1" -> MessageEnvelope(new ProtocolMessage(28, UUID.randomUUID(), 0, "foobar")),
        "key-2" -> MessageEnvelope(new ProtocolMessage(28, UUID.randomUUID(), 0, "foobar")),
        "key-3" -> MessageEnvelope(new ProtocolMessage(28, UUID.randomUUID(), 0, "foobar"))
      )

      publishToKafka("incoming", toSend)

      val res = consumeNumberMessagesFrom[MessageEnvelope]("outgoing", 3)

      // enricher is invoked for every packet
      timesEnricherInvoked should equal(3)

      // context is added
      res.map(_.getContext[Int]("foo")) should equal(Seq(42, 42, 42))

      // ubirch packets are left untouched
      // NOTE: the .toStrings are needed, because ProtocolMessage doesn't override equals...
      res.map(_.ubirchPacket.toString) should equal(toSend.map(_._2.ubirchPacket.toString))
    }
  }
}
