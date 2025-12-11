
# VegobjektNotifikasjon

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **hendelseId** | **kotlin.Long** | Unik id for hendelsen |  |
| **hendelseType** | **kotlin.String** | Navnet på hendelsestypen: VegobjektImportert, VegobjektVersjonOpprettet, VegobjektVersjonEndret eller VegobjektVersjonFjernet |  |
| **vegobjektId** | **kotlin.Long** | Id-en til vegobjektet som hendelsen gjelder |  |
| **vegobjektTypeId** | **kotlin.Int** | Vegobjekttypeid-en til vegobjektet som hendelsen gjelder |  |
| **vegobjektVersjon** | **kotlin.Int** | Versjon av vegobjektet som hendelsen gjelder. Versjonen er 0 når hendelsen er VegobjektImportert |  |
| **tidspunkt** | [**kotlin.time.Instant**](kotlin.time.Instant.md) | Tidspunktet hendelsen ble opprettet |  |



