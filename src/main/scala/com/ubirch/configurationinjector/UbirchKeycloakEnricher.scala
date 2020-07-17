package com.ubirch.configurationinjector

import com.softwaremill.sttp._
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.NioMicroservice
import net.logstash.logback.argument.StructuredArguments.v
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods

class UbirchKeycloakEnricher(context: NioMicroservice.Context) extends Enricher with StrictLogging {
  implicit val sttpBackend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  private val deviceInfoUrl = context.config.getString("ubirch-device-info-url")

  lazy val getDeviceCached: String => Either[Throwable, String] =
    context.cached(getDevice _).buildCache(name = "device-cache")

  logger.info("device_info_url={}", deviceInfoUrl)

  def getDevice(token: String): Either[Throwable, String] = {
    for {
      uri <- Uri.parse(deviceInfoUrl).toEither
      response = sttp.get(uri).header("Authorization", s"bearer $token").send()
      body <- response.body.left.map(_ => new NoSuchElementException("No device info"))
    } yield {
      body
    }
  }

  override def enrich(input: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope] = {
    val enrichment = for {
      token <- input.findHeader("X-Ubirch-DeviceInfo-Token")
        .toRight(new NoSuchElementException("No X-Ubirch-DeviceInfo-Token header present"))

      responseBody <- getDeviceCached(token)

      parsedResponse <- JsonMethods.parseOpt(responseBody)
        .filter(_.isInstanceOf[JObject])
        .map(_.asInstanceOf[JObject])
        .toRight(new IllegalArgumentException("response body couldn't be parsed as json object"))

      // we extract the configuredResponse field and merge it back into the root, because that's what responder service
      // expects
      configuredNiomonResponse = parsedResponse \ "attributes" \ "configuredResponse"
    } yield parsedResponse.merge(JObject("configuredResponse" -> configuredNiomonResponse))

    enrichment.fold({ error =>
      val requestId = input.requestIdHeader().orNull
      logger.error(s"error while trying to enrich [{}]", v("requestId", requestId), error)
      // we ignore the errors here
      input
    }, { extraData =>
      val newContext = input.value().context.merge(extraData)
      input.copy(value = input.value().copy(context = newContext))
    })
  }
}
