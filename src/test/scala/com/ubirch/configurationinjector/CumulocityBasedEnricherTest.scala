package com.ubirch.configurationinjector

import java.util.UUID

import com.typesafe.config.ConfigFactory
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.{NioMicroservice, NioMicroserviceLive}
import com.ubirch.protocol.ProtocolMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import redis.embedded.RedisServer

//noinspection TypeAnnotation
class CumulocityBasedEnricherTest extends FlatSpec with Matchers with BeforeAndAfterAll {
  val config = ConfigFactory.load()

  // this is ignored by default because it requires having C8Y_* env variable cumulocity credentials set
  "CumulocityBasedEnricher" should "inject data from cumulocity to ubirch packet envelopes" ignore {
    val microserviceRedissonDonor = NioMicroserviceLive[String, String]("niomon-enricher", _ => ???)
    val redisson = microserviceRedissonDonor.redisson

    val record = new ConsumerRecord[String, MessageEnvelope]("foo", 0, 0, "bar",
      MessageEnvelope(new ProtocolMessage(28, UUID.fromString("957bdffc-1a62-11e9-92bb-c83ea7010f86"), 0, null)))

    val enricher = new CumulocityBasedEnricher(new NioMicroservice.Context(redisson, config.getConfig("niomon-enricher")))

    val newRecord = enricher.enrich(record)
    val fromCache = enricher.enrich(record)

    newRecord.value().context should equal (fromCache.value().context)
  }

  val redis = new RedisServer()

  override def beforeAll(): Unit = redis.start()
  override def afterAll(): Unit = redis.stop()
}
