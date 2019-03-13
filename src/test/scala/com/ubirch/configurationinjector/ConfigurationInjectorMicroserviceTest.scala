package com.ubirch.configurationinjector

import java.util.UUID

import akka.stream.scaladsl.{Keep, Sink, Source}
import com.ubirch.kafka._
import com.ubirch.protocol.ProtocolMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class ConfigurationInjectorMicroserviceTest extends FlatSpec with Matchers {
  "configuration injector microservice" should "inject configuration using given enricher" in {
    var timesEnricherInvoked = 0
    val enricher: Enricher = { record => timesEnricherInvoked += 1; record.withExtraContext("foo", 42) }

    val microservice = new ConfigurationInjectorMicroservice(_ => enricher)
    import microservice.materializer

    val records = List(mkArbitraryRecord(), mkArbitraryRecord(), mkArbitraryRecord())
    val source = Source(records)

    val res = Await.result(source.map(microservice.processRecord).toMat(Sink.seq)(Keep.right).run(), 3.seconds)

    // enricher is invoked for every packet
    timesEnricherInvoked should equal(3)

    // context is added
    res.map(_.value().getContext[Int]("foo")) should equal(Seq(42, 42, 42))

    // ubirch packets are left untouched
    res.map(_.value().ubirchPacket) should equal(records.map(_.value().ubirchPacket))
  }

  def mkArbitraryRecord(): ConsumerRecord[String, MessageEnvelope] = {
    new ConsumerRecord("topic", 0, 0, "some-key", MessageEnvelope(
      new ProtocolMessage(28, UUID.randomUUID(), 0, "foobar")
    ))
  }
}
