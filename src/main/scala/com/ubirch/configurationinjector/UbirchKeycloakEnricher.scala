package com.ubirch.configurationinjector

import com.softwaremill.sttp._
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.kafka.MessageEnvelope
import net.logstash.logback.argument.StructuredArguments.v
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods

class UbirchKeycloakEnricher(deviceInfoUrl: String) extends Enricher with StrictLogging {
  implicit val sttpBackend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  override def enrich(record: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope] = {
    val enrichment = for {
      token <- record.headersScala.get("X-Ubirch-DeviceInfo-Token")
        .toRight(new NoSuchElementException("No X-Ubirch-DeviceInfo-Token header present"))
      uri <- Uri.parse(deviceInfoUrl).toEither
      response = sttp.get(uri).header("Authorization", s"bearer $token").send()
      responseBody <- response.body.left.map(_ => new NoSuchElementException("No device info"))

      parsedResponse <- JsonMethods.parseOpt(responseBody)
        .filter(_.isInstanceOf[JObject])
        .map(_.asInstanceOf[JObject])
        .toRight(new IllegalArgumentException("response body couldn't be parsed as json object"))

      configuredNiomonResponse = parsedResponse \ "attributes" \ "configuredResponse"
    } yield parsedResponse.merge(JObject("configuredResponse" -> configuredNiomonResponse))

    enrichment.fold({ error =>
      logger.error(s"error while trying to enrich [{}]", v("requestId", record.key()), error)
      record
    }, { extraData =>
      val newContext = record.value().context.merge(extraData)
      record.copy(value = record.value().copy(context = newContext))
    })
  }
}
