package com.ubirch

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka._
import akka.stream.{ActorMaterializer, KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Keep, RestartSink, RestartSource, RunnableGraph, Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.kafka._
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

package object configurationinjector extends StrictLogging {
  val conf: Config = ConfigFactory.load
  implicit val system: ActorSystem = ActorSystem("configuration-injector")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val kafkaUrl: String = conf.getString("kafka.url")

  val producerConfig: Config = system.settings.config.getConfig("akka.kafka.producer")
  val producerSettings: ProducerSettings[String, MessageEnvelope] =
    ProducerSettings(producerConfig, new StringSerializer, EnvelopeSerializer)
      .withBootstrapServers(kafkaUrl)

  val consumerConfig: Config = system.settings.config.getConfig("akka.kafka.consumer")
  val consumerSettings: ConsumerSettings[String, MessageEnvelope] =
    ConsumerSettings(consumerConfig, new StringDeserializer, EnvelopeDeserializer)
      .withBootstrapServers(kafkaUrl)
      .withGroupId("configuration-injector")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val incomingTopic: String = conf.getString("kafka.topic.incoming")
  val outgoingTopic: String = conf.getString("kafka.topic.outgoing")

  val kafkaSource: Source[ConsumerMessage.CommittableMessage[String, MessageEnvelope], NotUsed] =
    RestartSource.withBackoff(
      minBackoff = 2.seconds,
      maxBackoff = 1.minute,
      randomFactor = 0.2
    ) { () => Consumer.committableSource(consumerSettings, Subscriptions.topics(incomingTopic)) }

  val kafkaSink: Sink[ProducerMessage.Envelope[String, MessageEnvelope, ConsumerMessage.Committable], NotUsed] =
    RestartSink.withBackoff(
      minBackoff = 2.seconds,
      maxBackoff = 1.minute,
      randomFactor = 0.2
    ) { () => Producer.commitableSink(producerSettings) }

  val injectorGraph: RunnableGraph[UniqueKillSwitch] = kafkaSource
    .viaMat(KillSwitches.single)(Keep.right)
    .map { message =>
      val record = CumulocityBasedEnricher.enrich(message.record)

      val producerRecord = record.toProducerRecord(outgoingTopic)
      ProducerMessage.Message(producerRecord, message.committableOffset)
    }.to(kafkaSink)
}
