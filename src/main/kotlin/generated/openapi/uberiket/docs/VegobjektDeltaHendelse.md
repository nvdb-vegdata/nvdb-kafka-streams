
# VegobjektDeltaHendelse

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **hendelseId** | **kotlin.Long** | Unik id for hendelsen |  |
| **vegobjektId** | **kotlin.Long** | Id-en til vegobjektet som hendelsen gjelder |  |
| **vegobjektType** | **kotlin.Int** | Vegobjekttypeid-en til vegobjektet som hendelsen gjelder |  |
| **tidspunkt** | [**kotlin.time.Instant**](kotlin.time.Instant.md) | Tidspunktet hendelsen ble opprettet |  |
| **hendelseType** | **kotlin.String** | Navnet på hendelsestypen: VegobjektImportert, VegobjektVersjonOpprettet, VegobjektVersjonEndret eller VegobjektVersjonSlettet |  |
| **&#x60;data&#x60;** | [**VegobjektHendelse**](VegobjektHendelse.md) | Detaljer om hendelsen |  |



