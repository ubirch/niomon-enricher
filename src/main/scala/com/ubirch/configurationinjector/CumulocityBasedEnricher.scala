package com.ubirch.configurationinjector

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}

import c8y.{Hardware, IsDevice}
import com.cumulocity.model.JSONBase
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation
import com.cumulocity.sdk.client.inventory.{InventoryApi, InventoryFilter}
import com.cumulocity.sdk.client.{Platform, PlatformBuilder}
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.niomon.base.NioMicroservice
import com.ubirch.niomon.base.NioMicroservice.WithHttpStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods
import org.json4s.reflect.TypeInfo
import org.json4s.{Formats, JValue, Serializer}
import org.svenson.AbstractDynamicProperties

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

case class CumulocityBasedEnricher(context: NioMicroservice.Context) extends Enricher with StrictLogging {
  // Formats instance that delegates to cumulocity sdk serializer
  implicit val cumulocityFormats: Formats = com.ubirch.kafka.formats + new Serializer[AbstractDynamicProperties] {
    def deserialize(implicit format: Formats): PartialFunction[(json4s.TypeInfo, JValue), AbstractDynamicProperties] = {
      case (TypeInfo(c, _), json) if classOf[AbstractDynamicProperties].isAssignableFrom(c) =>
        JSONBase.fromJSON[AbstractDynamicProperties](
          JsonMethods.compact(json),
          c.asInstanceOf[Class[AbstractDynamicProperties]]
        )
    }

    def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case x: AbstractDynamicProperties => JsonMethods.parse(JSONBase.getJSONGenerator.forValue(x))
    }
  } ++ JodaTimeSerializers.all

  def enrich(record: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope] = {
    val uuid = record.value().ubirchPacket.getUUID
    val cumulocityInfo = getCumulocityInfo(record.headersScala)
    val cumulocityDevice = getDeviceCached(uuid, cumulocityInfo) match {
      case Some(device) => device
      case None =>
        logger.error(s"device [$uuid] not found in cumulocity")
        throw WithHttpStatus(404, new NoSuchElementException(s"Device [$uuid] not found in cumulocity"))
    }

    // TODO: add extra stuff from cumulocity
    //       look at `c8y.*` in com.nsn.cumulocity.model:device-capability-model maven lib
    //       possibly also our custom stuff that will be stored in cumulocity

    var r = record.withExtraContext(
      "hardwareInfo" -> cumulocityDevice.getField[Hardware],
      "customerId" -> cumulocityDevice.getOwner,
      "deviceName" -> cumulocityDevice.getName,
      "type" -> cumulocityDevice.getType,
      "creationTime" -> cumulocityDevice.getCreationDateTime,
      "lastUpdateTime" -> cumulocityDevice.getLastUpdatedDateTime,
      "cumulocityUrl" -> cumulocityDevice.getSelf
    )

    // Add custom response if it exists
    // TODO: now this is done through `c8y_Notes` field in the device, we should have our own field, but it's unclear
    //  how to make that nice with cumulocity UI
    val notes = cumulocityDevice.getProperty("c8y_Notes").asInstanceOf[String]
    if (notes != null) {
      val messageHeader = "ubirch-response"
      val messageFooter = "end-ubirch-response"

      val start = notes.indexOf(messageHeader)
      val end = notes.indexOf(messageFooter)
      if (start != -1 && end != -1) {
        val customResponse = notes.substring(start + messageHeader.length, end)
        r = r.withExtraContext("configuredResponse", JsonMethods.parse(customResponse))
      }
    }

    r
  }

  def getInventory(info: CumulocityInfo): InventoryApi = {
    val cumulocityClient: Platform = PlatformBuilder.platform()
      .withBaseUrl(info.baseUrl)
      .withTenant(info.tenant)
      .withPassword(info.password)
      .withUsername(info.username)
      .build()

    cumulocityClient.getInventoryApi
  }

  // TODO: this only supports basic auth for now
  def getCumulocityInfo(headers: Map[String, String]): CumulocityInfo = {
    val baseUrl = headers.getOrElse("X-Cumulocity-BaseUrl", context.config.getString("cumulocity.baseUrl"))
    val tenant = headers.getOrElse("X-Cumulocity-Tenant", context.config.getString("cumulocity.tenant"))

    val (username, password) = headers.get("Authorization") match {
      case Some(basicAuth) if basicAuth.startsWith("Basic ") =>
        val basicAuthDecoded = new String(Base64.getDecoder.decode(basicAuth.stripPrefix("Basic ")), StandardCharsets.UTF_8)
        val Array(user, pass) = basicAuthDecoded.split(":", 2)
        (user, pass)
      case None =>
        (context.config.getString("cumulocity.username"), context.config.getString("cumulocity.password"))
    }

    CumulocityInfo(baseUrl, tenant, username, password)
  }

  lazy val getDeviceCached: (UUID, CumulocityInfo) => Option[ManagedObjectRepresentation] =
    context.cached(getDevice _).buildCache(name = "device-cache")

  def getDevice(uuid: UUID, cumulocityInfo: CumulocityInfo): Option[ManagedObjectRepresentation] = {
    val inventoryApi = getInventory(cumulocityInfo)
    val uuidStr = uuid.toString
    val cumulocityFilter = InventoryFilter.searchInventory().byFragmentType(classOf[IsDevice]).byText(uuidStr)

    val devices = inventoryApi.getManagedObjectsByFilter(cumulocityFilter)

    devices.get().allPages().asScala.find { dev =>
      val hardware = dev.getField[Hardware]
      hardware != null && hardware.getSerialNumber == uuidStr
    }
  }

  private implicit class RichManagedObjectRepresentation(val managedObject: ManagedObjectRepresentation) {
    def getField[T](implicit ct: ClassTag[T]): T = managedObject.get(ct.runtimeClass.asInstanceOf[Class[T]])
  }

  case class CumulocityInfo(baseUrl: String, tenant: String, username: String, password: String)

}
