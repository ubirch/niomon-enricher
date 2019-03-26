package com.ubirch.configurationinjector

import java.util.UUID

import c8y.Hardware
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.NioMicroservice
import com.ubirch.protocol.ProtocolMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.nustaq.serialization.FSTConfiguration
import org.redisson.Redisson
import org.redisson.codec.FstCodec
import org.redisson.config.Config
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import redis.embedded.RedisServer

//noinspection TypeAnnotation
class CumulocityBasedEnricherTest extends FlatSpec with Matchers with BeforeAndAfterAll {
  val config = ConfigFactory.load()

  // this is ignored by default because it requires having C8Y_* env variable cumulocity credentials set
  "CumulocityBasedEnricher" should "inject data from cumulocity to ubirch packet envelopes" ignore {
    val redissonConfig = Config.fromJSON(config.getConfig("redisson").root().render(ConfigRenderOptions.concise()))
    redissonConfig.setCodec(new FstCodec(FSTConfiguration.createDefaultConfiguration().setForceSerializable(true)))
    val redisson = Redisson.create(redissonConfig)

    val record = new ConsumerRecord[String, MessageEnvelope]("foo", 0, 0, "bar",
      MessageEnvelope(new ProtocolMessage(28, UUID.fromString("957bdffc-1a62-11e9-92bb-c83ea7010f86"), 0, null)))

    val enricher = CumulocityBasedEnricher(new NioMicroservice.Context(redisson, config.getConfig("configuration-injector")))

    val newRecord = enricher.enrich(record)
    val fromCache = enricher.enrich(record)

    newRecord.value().context should equal (fromCache.value().context)
  }

  val redis = new RedisServer()

  override def beforeAll(): Unit = redis.start()
  override def afterAll(): Unit = redis.stop()
}
