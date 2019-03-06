package com.ubirch.configurationinjector

import java.util.UUID

import akka.kafka.ConsumerMessage
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.ubirch.kafka._
import com.ubirch.protocol.ProtocolMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class ConfigurationInjectorFlowTest extends FlatSpec with Matchers {
  "configuration injector flow" should "inject configuration using given enricher" in {
    var timesEnricherInvoked = 0
    val enricher: Enricher = { record => timesEnricherInvoked += 1; record.withExtraContext("foo", 42) }

    val messages = List(mkArbitraryMessage(), mkArbitraryMessage(), mkArbitraryMessage())
    val source = Source(messages)

    val res = Await.result(source.via(injectorFlow(enricher)).toMat(Sink.seq)(Keep.right).run(), 3.seconds)

    // enricher is invoked for every packet
    timesEnricherInvoked should equal(3)

    // context is added
    res.map(_.record.value().getContext[Int]("foo")) should equal(Seq(42, 42, 42))

    // ubirch packets are left untouched
    res.map(_.record.value().ubirchPacket) should equal(messages.map(_.record.value().ubirchPacket))
  }

  def mkArbitraryMessage(): ConsumerMessage.CommittableMessage[String, MessageEnvelope] = {
    new ConsumerMessage.CommittableMessage[String, MessageEnvelope](
      new ConsumerRecord("topic", 0, 0, "some-key", MessageEnvelope(
        new ProtocolMessage(28, UUID.randomUUID(), 0, "foobar")
      )), null)
  }
}
