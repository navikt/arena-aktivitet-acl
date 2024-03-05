package no.nav.arena_tiltak_aktivitet_acl.historiserteDeltakerFix

import no.nav.arena_tiltak_aktivitet_acl.domain.db.ArenaDataDbo
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.arena.OperationPos
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.arena.tiltak.DeltakelseId
import no.nav.arena_tiltak_aktivitet_acl.repositories.arenaDataRowMapper
import no.nav.arena_tiltak_aktivitet_acl.utils.getLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.*

@Component
class HistoriskDeltakelseRepo(
	private val template: NamedParameterJdbcTemplate
) {
	private val log = LoggerFactory.getLogger(javaClass)
	fun getHistoriskeDeltakelser(): List<HistoriskDeltakelse> {
		val query = """
			SELECT * FROM hist_tiltakdeltaker
			WHERE hist_tiltakdeltaker.fix_metode is null
			ORDER BY person_id, tiltakgjennomforing_id, rekkefolge
			LIMIT 1000
		""".trimIndent()
		val result = template.query(query) { resultSet, _ -> resultSet.toHistoriskDeltakelse() }
		log.info("Hentet ${result.size} historiske deltakelser")
		return result
	}
	fun oppdaterFixMetode(fixMetode: FixMetode) : Int {
		val query = """
		UPDATE hist_tiltakdeltaker SET fix_metode = :fixMetode, generated_deltakerid = :generertDeltakerId, generated_pos = :generertPos
		WHERE hist_tiltakdeltaker_id = :hist_tiltakdeltaker_id
	""".trimIndent()
		val muligPos = when(fixMetode) {
			is Ignorer -> null
			is Oppdater -> fixMetode.generertPos
			is Opprett -> fixMetode.generertPos
			is OpprettMedLegacyId -> fixMetode.generertPos
		}
		val params =
			mapOf("hist_tiltakdeltaker_id" to fixMetode.historiskDeltakelseId,
				"fixMetode" to fixMetode.navn(),
				"generertDeltakerId" to fixMetode.deltakelseId.value,
				"generertPos" to muligPos?.value)
		val result = template.update(query, params)
		log.info("Oppdaterte fixMetode for hist_tiltakdeltaker_id {${fixMetode.historiskDeltakelseId} antall rader $result")
		return result
	}

	private fun FixMetode.navn() {
		when (this) {
			is Ignorer -> "Ignorer"
			is Oppdater -> "Oppdater"
			is Opprett -> "Opprett"
			is OpprettMedLegacyId -> "OpprettMedLegacyId"
		}
	}

	data class DeltakelsePaaGjennomforing(
		val gjennomforingId: Long,
		val deltakelseId: DeltakelseId,
		val rekkefolge: Int,
		val latestOperationPos: String,
		val latestModDato: LocalDateTime
	)



	fun finnEksisterendeDeltakelserForGjennomforing(personId: Long, tiltakgjennomforingId: Long): List<DeltakelsePaaGjennomforing> {
		val query = """
			SELECT person_id, deltaker_id, gjennomforing_id, rekkefolge, latest_operation_pos, latest_mod_dato
			from deltaker_gjennomforing
			where person_id = :person_id
			and gjennomforing_id = :gjennomforing_id
			order by rekkefolge
		""".trimIndent()
		val result = template.query(query, mapOf("person_id" to personId, "gjennomforing_id" to tiltakgjennomforingId)) {
			resultSet, _ -> DeltakelsePaaGjennomforing(
				resultSet.getLong("gjennomforing_id"),
				DeltakelseId(resultSet.getLong("deltaker_id")),
				resultSet.getInt("rekkefolge"),
				resultSet.getString("latest_operation_pos"),
				resultSet.getLocalDateTime("latest_mod_dato")
				)
		}
		log.info("Fant ${result.size} resultat for personId $personId gjennomføringsId $tiltakgjennomforingId")
		return result
	}

	fun getLegacyId(personId: Long, gjennomforingId: Long /*datoStatusEndring: String?*/): LegacyId? {
		val sql = """
			select translation.arena_id as deltakerId, translation.aktivitet_id as funksjonellId
			from hist_tiltakdeltaker
				join dobledeltakelser on hist_tiltakdeltaker.person_id = dobledeltakelser.person_id
				join translation on dobledeltakelser.tiltakdeltaker_id = translation.arena_id
				and hist_tiltakdeltaker.tiltakgjennomforing_id = dobledeltakelser.tiltakgjennomforing_id
				and dobledeltakelser.jn_operation = 'DEL'
				and dobledeltakelser.person_id = :person_id
				and dobledeltakelser.tiltakgjennomforing_id = :gjennomforing_id
				and to_timestamp(dobledeltakelser.dato_statusendring, 'YYYY-MM-DD HH24:MI:SS')
						= to_timestamp(hist_tiltakdeltaker.dato_statusendring, 'DD.MM.YYYY HH24:MI:SS');
		""".trimIndent()
		val params = mapOf(
			"person_id" to personId,
			"gjennomforing_id" to gjennomforingId,
//			"datoStatusEndring" to datoStatusEndring
		)
		return runCatching { template.queryForObject(sql, params)
			{ rs, _ -> LegacyId(UUID.fromString(rs.getString("funksjonellId")), DeltakelseId(rs.getLong("deltakerId"))) }
		}.getOrNull()
	}

	fun getNextFreeDeltakerId(forrigeLedige: DeltakelseId): DeltakelseId {
		val sql = """
			select ledig.ledig
			from generate_series(:nesteMinDeltakelseId, 1000000) as ledig
			where ledig.ledig not in (
			    select deltaker_id from deltaker_gjennomforing
			    where deltaker_id between 0 AND 1000000
			) limit 1
		""".trimIndent()
		val nesteMinDeltakelseId = forrigeLedige.value + 1
		val params = mapOf("nesteMinDeltakelseId" to nesteMinDeltakelseId)
		return template.queryForObject(sql, params) { row, _ -> row.getLong(1) }
			.let { DeltakelseId(it) }
	}

	fun getMostRecentDeltakelse(deltakelseArenaId: DeltakelseId, operationPos: OperationPos): ArenaDataDbo? {
		val sql = """
				SELECT *
				FROM arena_data WHERE
					arena_id = :deltakelseId AND arena_table_name = 'SIAMO.TILTAKDELTAKER' and operation_pos = :operationPos;
		""".trimIndent()
		return template.queryForObject(sql, mapOf("deltakelseId" to deltakelseArenaId.value.toString(), "operationPos" to operationPos.value), arenaDataRowMapper)
	}

}



fun ResultSet.toHistoriskDeltakelse(): HistoriskDeltakelse {
	return HistoriskDeltakelse(
		hist_tiltakdeltaker_id = this.getLong("hist_tiltakdeltaker_id"),
		person_id = this.getLong("person_id"),
			tiltakgjennomforing_id = this.getLong("tiltakgjennomforing_id"),
			deltakerstatuskode = this.getString("deltakerstatuskode"),
			deltakertypekode = this.getString("deltakertypekode"),
			aarsakverdikode_status = this.getString("aarsakverdikode_status"),
			oppmotetypekode = this.getString("oppmotetypekode"),
			prioritet = this.getString("prioritet"),
			prosent_deltid = this.getString("prosent_deltid"),
			brukerid_statusendring = this.getString("brukerid_statusendring"),
			dato_statusendring = this.getString("dato_statusendring"),
			dato_svarfrist = this.getString("dato_svarfrist"),
			dato_fra = this.getString("dato_fra"),
			dato_til = this.getString("dato_til"),
			aktivitet_id = this.getString("aktivitet_id"),
			reg_dato = this.getString("reg_dato"),
			reg_user = this.getString("reg_user"),
			mod_dato = this.getString("mod_dato"),
			mod_user = this.getString("mod_user"),
			brukerid_endring_prioritering = this.getString("brukerid_endring_prioritering"),
			dato_endring_prioritering = this.getString("dato_endring_prioritering"),
			dokumentkode_siste_brev = this.getString("dokumentkode_siste_brev"),
			rekkefolge = this.getInt("rekkefolge"),

	)
}

data class LegacyId(
	val funksjonellId: UUID,
	val deltakerId: DeltakelseId
)
