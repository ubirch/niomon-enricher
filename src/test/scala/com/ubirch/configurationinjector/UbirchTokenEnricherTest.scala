package com.ubirch.configurationinjector

import java.util.UUID

import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.NioMicroservice.WithHttpStatus
import com.ubirch.protocol.ProtocolMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{FlatSpec, Matchers}
import org.json4s._

class UbirchTokenEnricherTest extends FlatSpec with Matchers {

  "UbirchTokenEnricher" should "verify correctly" in {

    val tokenEnricher =  new UbirchTokenEnricher

    val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL3Rva2VuLmRldi51YmlyY2guY29tIiwic3ViIjoiOTYzOTk1ZWQtY2UxMi00ZWE1LTg5ZGMtYjE4MTcwMWQxZDdiIiwiYXVkIjpbImh0dHBzOi8vYXBpLmNvbnNvbGUuZGV2LnViaXJjaC5jb20iLCJodHRwczovL25pb21vbi5kZXYudWJpcmNoLmNvbSIsImh0dHBzOi8vdmVyaWZ5LmRldi51YmlyY2guY29tIl0sImV4cCI6NzkyODAwMDg1MiwiaWF0IjoxNjE2NjEwNDUyLCJqdGkiOiJmMTMwNmJhYi0zODIzLTRkYWUtYTIxZS03ZmJiMDFiNmI5ODciLCJzY3AiOlsidGhpbmc6Y3JlYXRlIiwidXBwOmFuY2hvciIsInVwcDp2ZXJpZnkiXSwicHVyIjoiS2luZyBEdWRlIC0gQ29uY2VydCIsInRncCI6W10sInRpZCI6WyI4NDBiN2UyMS0wM2U5LTRkZTctYmIzMS0wYjk1MjRmM2I1MDAiXSwib3JkIjpbImh0dHA6Ly92ZXJpZmljYXRpb24uZGV2LnViaXJjaC5jb20iXX0.0-CA-dhgbRjzWbCjX1e3B08bSiPDbeZfBDb85uJPf3rEuNNH6MeVk0RKt2MVq7DMYco_c5Wolf09wdKX8kRrIA"

    val record = new ConsumerRecord[String, MessageEnvelope]("foo", 0, 0, "bar",
      MessageEnvelope(new ProtocolMessage(28, UUID.fromString("840b7e21-03e9-4de7-bb31-0b9524f3b500"), 0, null)))
      .withExtraHeaders("X-Ubirch-DeviceInfo-Token" -> token)
      .withExtraHeaders("X-Ubirch-Hardware-Id" -> "840b7e21-03e9-4de7-bb31-0b9524f3b500")

    lazy val enriched = tokenEnricher.enrich(record)

    assert(enriched.isInstanceOf[ConsumerRecord[String, MessageEnvelope]])

    val obj = for{
      JObject(child) <- enriched.value().context
      JField("hwDeviceId", JString("840b7e21-03e9-4de7-bb31-0b9524f3b500")) <- child
      JField("description", JString("")) <- child
      JField("customerId", JString("963995ed-ce12-4ea5-89dc-b181701d1d7b")) <- child
    } yield true


    assert(obj == List(true))

  }

  it should "verify fail if hardwareId doesn't match" in {

    val tokenEnricher =  new UbirchTokenEnricher

    val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL3Rva2VuLmRldi51YmlyY2guY29tIiwic3ViIjoiOTYzOTk1ZWQtY2UxMi00ZWE1LTg5ZGMtYjE4MTcwMWQxZDdiIiwiYXVkIjpbImh0dHBzOi8vYXBpLmNvbnNvbGUuZGV2LnViaXJjaC5jb20iLCJodHRwczovL25pb21vbi5kZXYudWJpcmNoLmNvbSIsImh0dHBzOi8vdmVyaWZ5LmRldi51YmlyY2guY29tIl0sImV4cCI6NzkyODAwMDg1MiwiaWF0IjoxNjE2NjEwNDUyLCJqdGkiOiJmMTMwNmJhYi0zODIzLTRkYWUtYTIxZS03ZmJiMDFiNmI5ODciLCJzY3AiOlsidGhpbmc6Y3JlYXRlIiwidXBwOmFuY2hvciIsInVwcDp2ZXJpZnkiXSwicHVyIjoiS2luZyBEdWRlIC0gQ29uY2VydCIsInRncCI6W10sInRpZCI6WyI4NDBiN2UyMS0wM2U5LTRkZTctYmIzMS0wYjk1MjRmM2I1MDAiXSwib3JkIjpbImh0dHA6Ly92ZXJpZmljYXRpb24uZGV2LnViaXJjaC5jb20iXX0.0-CA-dhgbRjzWbCjX1e3B08bSiPDbeZfBDb85uJPf3rEuNNH6MeVk0RKt2MVq7DMYco_c5Wolf09wdKX8kRrIA"

    val record = new ConsumerRecord[String, MessageEnvelope]("foo", 0, 0, "bar",
      MessageEnvelope(new ProtocolMessage(28, UUID.fromString("1b4bc7bf-d333-4fc5-97c7-93c16fc18970"), 0, null)))
      .withExtraHeaders("X-Ubirch-DeviceInfo-Token" -> token)
      .withExtraHeaders("X-Ubirch-Hardware-Id" -> "1b4bc7bf-d333-4fc5-97c7-93c16fc18970")

    assertThrows[WithHttpStatus](tokenEnricher.enrich(record))

  }

  it should "verify fail if token not found" in {

    val tokenEnricher =  new UbirchTokenEnricher

    val record = new ConsumerRecord[String, MessageEnvelope]("foo", 0, 0, "bar",
      MessageEnvelope(new ProtocolMessage(28, UUID.fromString("1b4bc7bf-d333-4fc5-97c7-93c16fc18970"), 0, null)))
      .withExtraHeaders("X-Ubirch-Hardware-Id" -> "1b4bc7bf-d333-4fc5-97c7-93c16fc18970")

    assertThrows[WithHttpStatus](tokenEnricher.enrich(record))

  }

}
