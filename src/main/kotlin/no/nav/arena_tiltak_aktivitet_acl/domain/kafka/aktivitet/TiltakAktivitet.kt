package no.nav.arena_tiltak_aktivitet_acl.domain.kafka.aktivitet

import no.nav.arena_tiltak_aktivitet_acl.repositories.AktivitetDbo
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.aktivitet.AktivitetOrderData

data class TiltakAktivitet(
	override val id: UUID,
	val eksternReferanseId: Long,
	override val personIdent: String,
	override val tittel: String,
	override val startDato: LocalDate?,
	override val sluttDato: LocalDate?,
	override val beskrivelse: String?, // alle, men annen oppførsel på tiltak(jobbklubb)
	override val endretAv: String?,
	val tiltak: TiltakDto,
	val status: StatusDto,

	val arrangorNavn: String?,
	val deltakelseProsent: Float?,
	val dagerPerUke: Int?,

	val registrertDato: LocalDateTime,
	val statusEndretDato: LocalDateTime?,
) : AktivitetOrderData {

	fun toDbo(data: String) = AktivitetDbo(
		id = id,
		personIdent = personIdent,
		kategori = AktivitetKategori.TILTAKSAKTIVITET,
		data = data
	)
}

