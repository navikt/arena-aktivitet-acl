package no.nav.arena_tiltak_aktivitet_acl.integration.commands.deltaker

import no.nav.arena_tiltak_aktivitet_acl.domain.db.ArenaDataDbo
import no.nav.arena_tiltak_aktivitet_acl.domain.db.DeltakerAktivitetMappingDbo
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.aktivitet.Aktivitetskort
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.aktivitet.AktivitetskortHeaders
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.aktivitet.KafkaMessageDto
import no.nav.arena_tiltak_aktivitet_acl.repositories.OppfolginsPeriodeId
import org.junit.jupiter.api.fail

class HandledResult(
	position: String,
	arenaDataDbo: ArenaDataDbo,
	deltakerAktivitetMapping: List<DeltakerAktivitetMappingDbo>,
	val output: KafkaMessageDto,
	val headers: AktivitetskortHeaders
): AktivitetResult(position, arenaDataDbo, deltakerAktivitetMapping) {
	fun output(check: (data: KafkaMessageDto) -> Unit): AktivitetResult {
		check(output)
		return this
	}
	fun aktivitetskort(check: (aktivitetskort: Aktivitetskort) -> Unit): AktivitetResult {
		check(output.aktivitetskort)
		return this
	}
	override fun result(check: (arenaDataDbo: ArenaDataDbo, deltakerAktivitetMapping: List<DeltakerAktivitetMappingDbo>, output: KafkaMessageDto?) -> Unit): AktivitetResult {
		check(arenaDataDbo, deltakerAktivitetMapping, output)
		return this
	}
}

open class AktivitetResult(
	val position: String,
	val arenaDataDbo: ArenaDataDbo,
	val deltakerAktivitetMapping: List<DeltakerAktivitetMappingDbo>
) {
	fun expectHandled(check: (data: HandledResult) -> Unit) {
		if (this !is HandledResult) fail("Expected arena message to have ingest status HANDLED but was ${this.arenaDataDbo.ingestStatus}")
		check(this)
	}
	fun arenaData(check: (data: ArenaDataDbo) -> Unit): AktivitetResult {
		check.invoke(arenaDataDbo)
		return this
	}

	open fun result(check: (arenaDataDbo: ArenaDataDbo, deltakerAktivitetMapping: List<DeltakerAktivitetMappingDbo>, output: KafkaMessageDto?) -> Unit): AktivitetResult {
		check(arenaDataDbo, deltakerAktivitetMapping, null)
		return this
	}
}
