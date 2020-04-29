package com.ubirch.configurationinjector
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.NioMicroservice
import org.apache.kafka.clients.consumer.ConsumerRecord

/** Enricher that dispatches between the cumulocity- and keycloak-based enrichers */
class MultiEnricher(context: NioMicroservice.Context) extends Enricher {
  val cumulocityBasedEnricher = new CumulocityBasedEnricher(context)
  val ubirchKeycloakEnricher = new UbirchKeycloakEnricher(context)

  override def enrich(record: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope] = {
    record.findHeader("X-Ubirch-Auth-Type") match {
      case Some("keycloak") | Some("ubirch") => ubirchKeycloakEnricher.enrich(record)
      case _ => cumulocityBasedEnricher.enrich(record)
    }
  }
}
