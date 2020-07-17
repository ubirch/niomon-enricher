package com.ubirch.configurationinjector
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.NioMicroservice
import org.apache.kafka.clients.consumer.ConsumerRecord

/** Enricher that dispatches between the cumulocity- and keycloak-based enrichers */
class MultiEnricher(context: NioMicroservice.Context) extends Enricher {
  val cumulocityBasedEnricher = new CumulocityBasedEnricher(context)
  val ubirchKeycloakEnricher = new UbirchKeycloakEnricher(context)

  override def enrich(input: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope] = {
    input.findHeader("X-Ubirch-Auth-Type") match {
      case Some("keycloak") | Some("ubirch") => ubirchKeycloakEnricher.enrich(input)
      case _ => cumulocityBasedEnricher.enrich(input)
    }
  }
}
