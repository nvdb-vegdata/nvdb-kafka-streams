package no.geirsagberg.kafkaathome.service

import no.geirsagberg.kafkaathome.api.NvdbApiClient
import no.geirsagberg.kafkaathome.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class GeometryEnrichmentServiceTest {

    private lateinit var nvdbApiClient: NvdbApiClient
    private lateinit var enrichmentService: GeometryEnrichmentService

    @BeforeEach
    fun setup() {
        nvdbApiClient = mock(NvdbApiClient::class.java)
        enrichmentService = GeometryEnrichmentService(nvdbApiClient)
    }

    @Test
    fun `should enrich vegobjekt with geometries from veglenker`() {
        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            stedfesting = Stedfesting(
                type = "punkt",
                veglenkesekvensider = listOf(
                    VeglenkeStedfesting(veglenkesekvensId = 1L),
                    VeglenkeStedfesting(veglenkesekvensId = 2L)
                )
            )
        )

        val veglenker = listOf(
            Veglenke(
                veglenkeId = 101L,
                geometri = Geometri(wkt = "LINESTRING(10.0 60.0, 10.1 60.1)")
            ),
            Veglenke(
                veglenkeId = 102L,
                geometri = Geometri(wkt = "LINESTRING(10.1 60.1, 10.2 60.2)")
            )
        )

        `when`(nvdbApiClient.fetchVeglenkerByIdsBlocking(listOf(1L, 2L)))
            .thenReturn(veglenker)

        val enriched = enrichmentService.enrichWithGeometry(vegobjekt)

        assertNotNull(enriched.stedfesting)
        assertNotNull(enriched.stedfesting?.geometries)
        assertEquals(2, enriched.stedfesting?.geometries?.size)
        assertEquals("LINESTRING(10.0 60.0, 10.1 60.1)", enriched.stedfesting?.geometries?.get(0))
        assertEquals("LINESTRING(10.1 60.1, 10.2 60.2)", enriched.stedfesting?.geometries?.get(1))

        verify(nvdbApiClient).fetchVeglenkerByIdsBlocking(listOf(1L, 2L))
    }

    @Test
    fun `should return original vegobjekt when stedfesting is null`() {
        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            stedfesting = null
        )

        val enriched = enrichmentService.enrichWithGeometry(vegobjekt)

        assertSame(vegobjekt, enriched)
        verifyNoInteractions(nvdbApiClient)
    }

    @Test
    fun `should return original vegobjekt when veglenkesekvensider is null`() {
        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            stedfesting = Stedfesting(
                type = "punkt",
                veglenkesekvensider = null
            )
        )

        val enriched = enrichmentService.enrichWithGeometry(vegobjekt)

        assertSame(vegobjekt, enriched)
        verifyNoInteractions(nvdbApiClient)
    }

    @Test
    fun `should return original vegobjekt when veglenkesekvensider is empty`() {
        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            stedfesting = Stedfesting(
                type = "punkt",
                veglenkesekvensider = emptyList()
            )
        )

        val enriched = enrichmentService.enrichWithGeometry(vegobjekt)

        assertSame(vegobjekt, enriched)
        verifyNoInteractions(nvdbApiClient)
    }

    @Test
    fun `should filter out veglenker with null geometry`() {
        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            stedfesting = Stedfesting(
                type = "punkt",
                veglenkesekvensider = listOf(
                    VeglenkeStedfesting(veglenkesekvensId = 1L)
                )
            )
        )

        val veglenker = listOf(
            Veglenke(
                veglenkeId = 101L,
                geometri = Geometri(wkt = "LINESTRING(10.0 60.0, 10.1 60.1)")
            ),
            Veglenke(
                veglenkeId = 102L,
                geometri = null
            ),
            Veglenke(
                veglenkeId = 103L,
                geometri = Geometri(wkt = null)
            )
        )

        `when`(nvdbApiClient.fetchVeglenkerByIdsBlocking(listOf(1L)))
            .thenReturn(veglenker)

        val enriched = enrichmentService.enrichWithGeometry(vegobjekt)

        assertNotNull(enriched.stedfesting?.geometries)
        assertEquals(1, enriched.stedfesting?.geometries?.size)
        assertEquals("LINESTRING(10.0 60.0, 10.1 60.1)", enriched.stedfesting?.geometries?.get(0))
    }

    @Test
    fun `should return original vegobjekt when API call fails`() {
        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            stedfesting = Stedfesting(
                type = "punkt",
                veglenkesekvensider = listOf(
                    VeglenkeStedfesting(veglenkesekvensId = 1L)
                )
            )
        )

        `when`(nvdbApiClient.fetchVeglenkerByIdsBlocking(listOf(1L)))
            .thenThrow(RuntimeException("API Error"))

        val enriched = enrichmentService.enrichWithGeometry(vegobjekt)

        assertSame(vegobjekt, enriched)
        assertNull(vegobjekt.stedfesting?.geometries)
    }

    @Test
    fun `should return original vegobjekt when no geometries found`() {
        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            stedfesting = Stedfesting(
                type = "punkt",
                veglenkesekvensider = listOf(
                    VeglenkeStedfesting(veglenkesekvensId = 1L)
                )
            )
        )

        val veglenker = listOf(
            Veglenke(veglenkeId = 101L, geometri = null)
        )

        `when`(nvdbApiClient.fetchVeglenkerByIdsBlocking(listOf(1L)))
            .thenReturn(veglenker)

        val enriched = enrichmentService.enrichWithGeometry(vegobjekt)

        assertSame(vegobjekt, enriched)
    }

    @Test
    fun `should filter out null veglenkesekvensId values`() {
        val vegobjekt = Vegobjekt(
            id = 12345L,
            typeId = 105,
            stedfesting = Stedfesting(
                type = "punkt",
                veglenkesekvensider = listOf(
                    VeglenkeStedfesting(veglenkesekvensId = 1L),
                    VeglenkeStedfesting(veglenkesekvensId = null),
                    VeglenkeStedfesting(veglenkesekvensId = 2L)
                )
            )
        )

        val veglenker = listOf(
            Veglenke(
                veglenkeId = 101L,
                geometri = Geometri(wkt = "LINESTRING(10.0 60.0, 10.1 60.1)")
            )
        )

        `when`(nvdbApiClient.fetchVeglenkerByIdsBlocking(listOf(1L, 2L)))
            .thenReturn(veglenker)

        val enriched = enrichmentService.enrichWithGeometry(vegobjekt)

        assertNotNull(enriched.stedfesting?.geometries)
        verify(nvdbApiClient).fetchVeglenkerByIdsBlocking(listOf(1L, 2L))
    }
}
