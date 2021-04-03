package com.ubirch.configurationinjector

import com.softwaremill.sttp._
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.NioMicroservice
import com.ubirch.niomon.base.NioMicroservice.WithHttpStatus
import net.logstash.logback.argument.StructuredArguments.v
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.Formats
import org.json4s._
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods

class UbirchKeycloakEnricher(context: NioMicroservice.Context) extends UbirchEnricher {

  implicit val sttpBackend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  private val deviceInfoUrl = context.config.getString("ubirch-device-info-url")

  lazy val getDeviceCached: String => Either[Throwable, String] =
    context.cached(getDevice _).buildCache(name = "device-cache")

  logger.info("device_info_url={}", deviceInfoUrl)

  def getDevice(token: String): Either[Throwable, String] = {
    for {
      uri <- Uri.parse(deviceInfoUrl).toEither
      response = sttp.get(uri).header("Authorization", s"bearer $token").send()
      body <- response.body.left.map(s => new NoSuchElementException(s"No device info [${s}]"))
    } yield {
      body
    }
  }

  override def enrich(record: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope] = {

    implicit val formats: Formats = com.ubirch.kafka.formats

    val enrichment: Either[Throwable, JObject] = for {
      token <- record
        .findHeader("X-Ubirch-DeviceInfo-Token")
        .toRight(new NoSuchElementException("No X-Ubirch-DeviceInfo-Token header present"))

      responseBody <- getDeviceCached(token)

      parsedResponse <- JsonMethods.parseOpt(responseBody)
        .filter(_.isInstanceOf[JObject])
        .map(_.asInstanceOf[JObject])
        .toRight(new IllegalArgumentException("response body couldn't be parsed as json object"))

      //This is an extra check on parsing what is expected.
      di <- scala.util.Try(Extraction.extract[DeviceInfo](parsedResponse))
        .recover {
          case _: Exception => throw new IllegalArgumentException("response body couldn't be materialized = " + parsedResponse.toString)
        }.toEither

      _ = logger.info("device_info={}", di.toString)

    } yield parsedResponse

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
