package com.ubirch.configurationinjector

import java.util.UUID

import com.ubirch.defaults.TokenApi
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.NioMicroservice.WithHttpStatus
import net.logstash.logback.argument.StructuredArguments.v
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.Formats
import org.json4s._
import org.json4s.JsonAST.JObject

class UbirchTokenEnricher extends UbirchEnricher {

  implicit private val formats: Formats = com.ubirch.kafka.formats

  override def enrich(record: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope] = {

    val enrichment: Either[Throwable, JObject] = for {
      token <- record
        .findHeader("X-Ubirch-DeviceInfo-Token")
        .toRight(new NoSuchElementException("No X-Ubirch-DeviceInfo-Token header present"))

      claims <- TokenApi.decodeAndVerify(token).toEither
      clientId <- claims.isSubjectUUID.toEither

      hardwareId <- record.findHeader("X-Ubirch-Hardware-Id")
        .map(UUID.fromString)
        .toRight(new NoSuchElementException("missing X-Ubirch-Hardware-Id header"))

      _ <- claims.validateIdentity(hardwareId).toEither

      //TODO: The extra attribute must be added here after adding support for them to the token manager service
      diObj <- scala.util.Try(DeviceInfo(hardwareId.toString, "", clientId.toString, Map.empty))
        .map(Extraction.decompose)
        .map(_.asInstanceOf[JObject])
        .recover {
          case _: Exception => throw new IllegalArgumentException("claims could not be materialized")
        }.toEither

    } yield diObj

    enrichment.fold({ error =>
      val requestId = record.requestIdHeader().orNull
      logger.error(s"error while trying to enrich [{}]", v("requestId", requestId), error)
      throw WithHttpStatus(400, error, Option(xcode(error)))
    }, { extraData =>
      val newContext = record.value().context.merge(extraData)
      record.copy(value = record.value().copy(context = newContext))
    })
  }

}
