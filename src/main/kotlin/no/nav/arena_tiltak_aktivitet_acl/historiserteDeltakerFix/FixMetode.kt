package no.nav.arena_tiltak_aktivitet_acl.historiserteDeltakerFix

import no.nav.arena_tiltak_aktivitet_acl.domain.db.ArenaDataUpsertInput
import no.nav.arena_tiltak_aktivitet_acl.domain.db.IngestStatus
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.aktivitet.Operation
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.arena.OperationPos
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.arena.tiltak.ArenaDeltakelse
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.arena.tiltak.DeltakelseId
import no.nav.arena_tiltak_aktivitet_acl.utils.ArenaTableName
import no.nav.arena_tiltak_aktivitet_acl.utils.asBackwardsFormattedLocalDateTime
import java.time.LocalDateTime
import java.util.*


sealed class FixMetode (val historiskDeltakelseId: Long, val deltakelseId: DeltakelseId) {
//	abstract fun toArenaDataUpsertInput(pos: OperationPos): ArenaDataUpsertInput?
}
class Ignorer(historiskDeltakelseId: Long, deltakelseId: DeltakelseId) : FixMetode(historiskDeltakelseId, deltakelseId) {
//	override fun toArenaDataUpsertInput(pos: OperationPos): ArenaDataUpsertInput? = null
}

class Oppdater(deltakelseId: DeltakelseId, val arenaDeltakelse: ArenaDeltakelse, val historiskDeltakelse: HistoriskDeltakelse, val generertPos: OperationPos): FixMetode(historiskDeltakelse.hist_tiltakdeltaker_id, deltakelseId) {
	fun toArenaDataUpsertInput(): ArenaDataUpsertInput {
		return historiskDeltakelseTilArenaDataUpsertInput(
			deltakelseId = deltakelseId,
			operation = Operation.MODIFIED,
			pos = generertPos,
			operationTimestamp = LocalDateTime.MIN,
			before = mapper.writeValueAsString(arenaDeltakelse),
			after = mapper.writeValueAsString(historiskDeltakelse.toArenaDeltakelse(deltakelseId))
		)
	}
}

class OpprettMedLegacyId(deltakelseId: DeltakelseId, val historiskDeltakelse: HistoriskDeltakelse, val funksjonellId: UUID, val generertPos: OperationPos): FixMetode(historiskDeltakelse.hist_tiltakdeltaker_id, deltakelseId) {
	fun toArenaDataUpsertInput(): ArenaDataUpsertInput {
		return historiskDeltakelseTilArenaDataUpsertInput(
			deltakelseId = deltakelseId,
			operation = Operation.CREATED,
			pos = generertPos,
			operationTimestamp = historiskDeltakelse.mod_dato.asBackwardsFormattedLocalDateTime(),
			before = null,
			after = mapper.writeValueAsString(historiskDeltakelse.toArenaDeltakelse(deltakelseId))
		)
	}
}

/**
 * Vi mangler deltakelse i arena_data for denne person-gjennomføring.
 * Det er sannsynligvis fordi den 'nye' deltakelsen ble IGNORED og at IGNORED-data tidligere ble slettet rutinemessig.
 */
class OpprettSingelHistorisk(deltakelseId: DeltakelseId, val historiskDeltakelse: HistoriskDeltakelse, val generertPos: OperationPos)
	: FixMetode(historiskDeltakelse.hist_tiltakdeltaker_id, deltakelseId) {
	fun toArenaDataUpsertInput(): ArenaDataUpsertInput {
		return historiskDeltakelseTilArenaDataUpsertInput(
			deltakelseId = deltakelseId,
			operation = Operation.CREATED,
			pos = generertPos,
			operationTimestamp = LocalDateTime.MIN,
			before = null,
			after = mapper.writeValueAsString(historiskDeltakelse.toArenaDeltakelse(deltakelseId))
		)
	}
}

class Opprett(deltakelseId: DeltakelseId, val historiskDeltakelse: HistoriskDeltakelse, val generertPos: OperationPos): FixMetode(historiskDeltakelse.hist_tiltakdeltaker_id, deltakelseId) {
	fun toArenaDataUpsertInput(): ArenaDataUpsertInput {
		return historiskDeltakelseTilArenaDataUpsertInput(
			deltakelseId = deltakelseId,
			operation = Operation.CREATED,
			pos = generertPos,
			operationTimestamp = LocalDateTime.MIN,
			before = null,
			after = mapper.writeValueAsString(historiskDeltakelse.toArenaDeltakelse(deltakelseId))
		)
	}
}

fun historiskDeltakelseTilArenaDataUpsertInput(deltakelseId: DeltakelseId, operation: Operation, pos: OperationPos, operationTimestamp: LocalDateTime, before: String?, after: String?): ArenaDataUpsertInput {
	return ArenaDataUpsertInput(
		ArenaTableName.DELTAKER,
		arenaId = deltakelseId.toString(),
		operation = operation,
		operationPosition = pos,
		operationTimestamp = operationTimestamp,
		ingestStatus = IngestStatus.NEW,
		ingestedTimestamp = LocalDateTime.now(),
		before = before,
		after = after
	)
}
