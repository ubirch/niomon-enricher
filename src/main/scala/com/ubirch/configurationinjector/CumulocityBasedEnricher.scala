package com.ubirch.configurationinjector

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}

import c8y.{Hardware, IsDevice}
import com.cumulocity.model.JSONBase
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation
import com.cumulocity.sdk.client.inventory.{InventoryApi, InventoryFilter}
import com.cumulocity.sdk.client.{Platform, PlatformBuilder}
import com.ubirch.kafka.MessageEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s
import org.json4s.jackson.JsonMethods
import org.json4s.reflect.TypeInfo
import org.json4s.{Formats, JValue, Serializer}
import org.svenson.AbstractDynamicProperties

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

object CumulocityBasedEnricher extends Enricher {
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
  }

  def enrich(record: ConsumerRecord[String, MessageEnvelope]): ConsumerRecord[String, MessageEnvelope] = {
    val uuid = record.value().ubirchPacket.getUUID
    val cumulocityInfo = getCumulocityInfo(record.headersScala)
    val cumulocityDevice = getDevice(uuid, getInventory(cumulocityInfo)) match {
      case Some(device) => device
      case None => return record.withExtraContext("error", "device not found in cumulocity")
    }

    val hardware = cumulocityDevice.getField[Hardware]

    // TODO: add extra stuff from cumulocity
    //       look at `c8y.*` in com.nsn.cumulocity.model:device-capability-model maven lib
    //       possibly also our custom stuff that will be stored in cumulocity

    record.withExtraContext(
      "cumulocityInfo" -> cumulocityInfo,
      "hardwareInfo" -> hardware,
      "owner" -> cumulocityDevice.getOwner,
      "deviceName" -> cumulocityDevice.getName,
      "type" -> cumulocityDevice.getType,
      "creationTime" -> cumulocityDevice.getCreationTime, // json4s doesn't support DateTime
      "lastUpdateTime" -> cumulocityDevice.getLastUpdated,
      "cumulocityUrl" -> cumulocityDevice.getSelf
    )
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
    val baseUrl = headers.getOrElse("X-Cumulocity-BaseUrl", conf.getString("cumulocity.baseUrl"))
    val tenant = headers.getOrElse("X-Cumulocity-Tenant", conf.getString("cumulocity.tenant"))

    val (username, password) = headers.get("Authorization") match {
      case Some(basicAuth) if basicAuth.startsWith("Basic ") =>
        val basicAuthDecoded = new String(Base64.getDecoder.decode(basicAuth.stripPrefix("Basic ")), StandardCharsets.UTF_8)
        val Array(user, pass) = basicAuthDecoded.split(":", 2)
        (user, pass)
      case None =>
        (conf.getString("cumulocity.username"), conf.getString("cumulocity.password"))
    }

    CumulocityInfo(baseUrl, tenant, username, password)
  }

  def getDevice(uuid: UUID, inventoryApi: InventoryApi): Option[ManagedObjectRepresentation] = {
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
