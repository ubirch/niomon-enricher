# Niomon Enricher
This microservice fetches additional data related to the sender of a UPP (a device) and attaches it to the message 
envelope in case of any downstream microservice needs it.

## Development
There are currently two different specific enrichers implemented: [CumulocityBasedEnricher](./src/main/scala/com/ubirch/configurationinjector/CumulocityBasedEnricher.scala),
and [UbirchKeycloakEnricher](./src/main/scala/com/ubirch/configurationinjector/UbirchKeycloakEnricher.scala). Their names
are pretty self-explanatory. There's also [MultiEnricher](./src/main/scala/com/ubirch/configurationinjector/MultiEnricher.scala),
which can dispatch between the two based on a kafka header value.

### Core Libraries
* cumulocity java-client
* sttp