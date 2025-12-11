
# Vegobjekt

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **id** | **kotlin.Long** |  |  |
| **versjon** | **kotlin.Int** |  |  |
| **typeId** | **kotlin.Int** |  |  |
| **sistEndret** | [**kotlin.time.Instant**](kotlin.time.Instant.md) |  |  |
| **gyldighetsperiode** | [**Gyldighetsperiode**](Gyldighetsperiode.md) | Tilstede dersom inkluder&#x3D;alle|gyldighetsperiode |  [optional] |
| **egenskaper** | [**kotlin.collections.Map&lt;kotlin.String, EgenskapVerdi&gt;**](EgenskapVerdi.md) | Tilstede dersom inkluder&#x3D;alle|egenskaper |  [optional] |
| **barn** | **kotlin.collections.Map&lt;kotlin.String, kotlin.collections.Set&lt;kotlin.Long&gt;&gt;** | Tilstede dersom inkluder&#x3D;alle|barn |  [optional] |
| **stedfesting** | [**Stedfesting**](Stedfesting.md) | Tilstede dersom inkluder&#x3D;alle|stedfesting og objektet er stedfestet på vegnettet |  [optional] |



