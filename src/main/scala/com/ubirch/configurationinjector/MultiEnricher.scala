package com.ubirch.configurationinjector
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.NioMicroservice
import org.apache.kafka.clients.consumer.ConsumerRecord

class MultiEnricher(context: NioMicroservice.Context) extends Enricher {
  val cumulocityBasedEnricher = new CumulocityBasedEnricher(context)
  val ubirchKeycloakEnricher = new UbirchKeycloakEnricher(context.config.getString("ubirch-device-info-url"))

  override def enrich(record: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope] = {
    record.headersScala.get("X-Ubirch-Auth-Type") match {
      case Some("keycloak") | Some("ubirch") => ubirchKeycloakEnricher.enrich(record)
      case _ => cumulocityBasedEnricher.enrich(record)
    }
  }
}