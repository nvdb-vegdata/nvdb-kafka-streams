
# VegobjektVersjonEndret

## Properties
| Name | Type | Description | Notes |
| ------------ | ------------- | ------------- | ------------- |
| **egenskapEndringer** | [**kotlin.collections.Map&lt;kotlin.String, EgenskapVerdi&gt;**](EgenskapVerdi.md) | Oppgir nye verdier for egenskaper som er endret |  |
| **barnEndringer** | [**kotlin.collections.Map&lt;kotlin.String, RelasjonEndring&gt;**](RelasjonEndring.md) | Beskriver hvilke relasjoner som er endret, erstattet, lagt til eller fjernet |  |
| **versjonId** | **kotlin.Int** | Hvilken versjon av vegobjektet som er endret |  |
| **gyldighetsperiode** | [**Gyldighetsperiode**](Gyldighetsperiode.md) | Ny gyldighetsperiode. Tilstede dersom gyldighetsperiode er endret |  [optional] |
| **stedfestingEndring** | [**StedfestingEndring**](StedfestingEndring.md) | Tilstede dersom stedfesting er endret, erstattet, lagt til eller fjernet |  [optional] |
| **originalVersjon** | [**VegobjektVersjon**](VegobjektVersjon.md) | Vegobjektet slik det så ut før endringen, hvis tilgjengelig |  [optional] |



