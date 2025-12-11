
# VegnettNotifikasjon

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **hendelseId** | **kotlin.Long** | Unik id for hendelsen |  |
| **nettelementId** | **kotlin.Long** | Id-en til nettelementet som hendelsen gjelder |  |
| **nettelementType** | **kotlin.Int** | Nettelementtypeid-en til nettelementet som hendelsen gjelder |  |
| **tidspunkt** | [**kotlin.time.Instant**](kotlin.time.Instant.md) | Tidspunktet hendelsen ble opprettet |  |
| **hendelseType** | **kotlin.String** | Navnet på hendelsestypen: VeglenkesekvensOpprettet, VeglenkesekvensEndret, VeglenkesekvensFjernet, NodeOpprettet, NodeEndret, NodeFjernet |  |



