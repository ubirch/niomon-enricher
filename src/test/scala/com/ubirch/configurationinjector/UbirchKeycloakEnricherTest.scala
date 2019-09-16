package com.ubirch.configurationinjector

import java.util.UUID

import com.ubirch.kafka.MessageEnvelope
import com.ubirch.protocol.ProtocolMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{FlatSpec, Matchers}

class UbirchKeycloakEnricherTest extends FlatSpec with Matchers {
  // this is ignored by default because it requires having C8Y_* env variable cumulocity credentials set
  "CumulocityBasedEnricher" should "inject data from cumulocity to ubirch packet envelopes" ignore {
    // when manually running this test, be sure to replace this with a fresh token for 1b4bc7bf-d333-4fc5-97c7-93c16fc18970
    val token = "eyJhbGciOiJFUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJfYVJxT3lmTkxyakNwY0x1dmxRWjFIWTJTNl9vMmVRQkl5UjFnTEZJVW80In0.eyJqdGkiOiIzY2NmMTBjMy04MjE3LTRmMTMtYjdhMC00NDhlY2I0OWM5NzAiLCJleHAiOjE1Njg2NDg4NDIsIm5iZiI6MCwiaWF0IjoxNTY4NjQ3NjQyLCJpc3MiOiJodHRwczovL2lkLmRldi51YmlyY2guY29tL2F1dGgvcmVhbG1zL3Rlc3QtcmVhbG0iLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiOGI3MDY5ZjUtNGJlMi00MjQ0LTkyYjUtZWZmYmU2NTZlNDljIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoidWJpcmNoLTIuMC11c2VyLWFjY2Vzcy1sb2NhbCIsImF1dGhfdGltZSI6MCwic2Vzc2lvbl9zdGF0ZSI6ImYyZThkMWFmLTI0YTMtNGE4Ny1iNDg3LWU2MzdmZjUyYWQ3NiIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiaHR0cDovLzE5Mi4xNjguMS4xNzMiLCJodHRwczovL2lkLmRldi51YmlyY2guY29tL2F1dGgvKyIsImh0dHBzOi8vY29uc29sZS5kZXYudWJpcmNoLmNvbS8rIiwiaHR0cDovL2xvY2FsaG9zdDo5MTAxIiwiaHR0cHM6Ly9jb25zb2xlLmRldi51YmlyY2guY29tIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyIsIkRFVklDRSIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJwcm9maWxlIGVtYWlsIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJuYW1lIjoiTWljaGFzIFRlc3QgMSIsInByZWZlcnJlZF91c2VybmFtZSI6IjFiNGJjN2JmLWQzMzMtNGZjNS05N2M3LTkzYzE2ZmMxODk3MCIsImZhbWlseV9uYW1lIjoiTWljaGFzIFRlc3QgMSJ9.MEUCIQCfqwWNHZuZKhgvnqESb-9ApRhOy9UeuiaFynrHTT2rYAIgZE9eFUV7sKfrFoHKM2-sff2ot_D56cdr7btCTdyYatY"

    val record = new ConsumerRecord[String, MessageEnvelope]("foo", 0, 0, "bar",
      MessageEnvelope(new ProtocolMessage(28, UUID.fromString("1b4bc7bf-d333-4fc5-97c7-93c16fc18970"), 0, null)))
      .withExtraHeaders("X-Ubirch-DeviceInfo-Token" -> token)

    val enricher = new UbirchKeycloakEnricher("https://api.console.dev.ubirch.com/ubirch-web-ui/api/v1/auth/deviceInfo")

    val newRecord = enricher.enrich(record)

    println(newRecord)
  }
}
