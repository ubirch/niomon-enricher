package com.ubirch.configurationinjector

import java.util.UUID

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

object CumulocityBasedEnricher {
  private val cumulocityClient: Platform = PlatformBuilder.platform()
    .withBaseUrl(conf.getString("cumulocity.baseUrl"))
    .withTenant(conf.getString("cumulocity.tenant"))
    .withPassword(conf.getString("cumulocity.password"))
    .withUsername(conf.getString("cumulocity.username"))
    .build()

  private val inventoryApi: InventoryApi = cumulocityClient.getInventoryApi

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
    val cumulocityDevice = getDevice(uuid) match {
      case Some(device) => device
      case None => return record.withExtraContext("error", "device not found in cumulocity")
    }

    val hardware = cumulocityDevice.getField[Hardware]

    // TODO: add extra stuff from cumulocity
    //       look at `c8y.*` in com.nsn.cumulocity.model:device-capability-model maven lib
    //       possibly also our custom stuff that will be stored in cumulocity

    record.withExtraContext(
      "hardwareInfo" -> hardware,
      "owner" -> cumulocityDevice.getOwner,
      "deviceName" -> cumulocityDevice.getName,
      "type" -> cumulocityDevice.getType,
      "creationTime" -> cumulocityDevice.getCreationTime, // json4s doesn't support DateTime
      "lastUpdateTime" -> cumulocityDevice.getLastUpdated,
      "cumulocityUrl" -> cumulocityDevice.getSelf
    )
  }

  def getDevice(uuid: UUID): Option[ManagedObjectRepresentation] = {
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

}
