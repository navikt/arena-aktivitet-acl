package no.nav.arena_tiltak_aktivitet_acl.processors.converters

import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.aktivitet.*
import no.nav.arena_tiltak_aktivitet_acl.domain.kafka.arena.TiltakDeltaker
import java.util.*

object ArenaDeltakerConverter {
	val JOBBKLUBB = "JOBBKLUBB"
	val AMO = "AMO"
	val GRUPPEAMO = "GRUPPEAMO"
	val ENKELAMO = "ENKELAMO"

	fun toAktivitetStatus(status: String): AktivitetStatus {
		return when (status) {
			"AKTUELL" -> AktivitetStatus.PLANLAGT
			"INFOMOETE" -> AktivitetStatus.PLANLAGT
			"JATAKK" -> AktivitetStatus.PLANLAGT
			"TILBUD" -> AktivitetStatus.PLANLAGT
			"VENTELISTE" -> AktivitetStatus.PLANLAGT
			"FULLF" -> AktivitetStatus.FULLFORT
			"GJENN" -> AktivitetStatus.GJENNOMFORES
			"GJENN_AVB" -> AktivitetStatus.AVBRUTT
			"GJENN_AVL" -> AktivitetStatus.AVBRUTT
			"IKKAKTUELL" -> AktivitetStatus.AVBRUTT
			"AVSLAG" -> AktivitetStatus.AVBRUTT
			"DELAVB" -> AktivitetStatus.AVBRUTT
			"IKKEM" -> AktivitetStatus.AVBRUTT
			"NEITAKK" -> AktivitetStatus.AVBRUTT
			else -> throw IllegalArgumentException("Ugyldig arenastatus $status kan ikke konverteres")
		}
	}

	fun toDeltakelseStatus(status: String): DeltakelseStatus? {
		return when (status) {
			"AKTUELL" -> DeltakelseStatus.SOKT_INN
			"INFOMOETE" -> DeltakelseStatus.INFOMOETE
			"JATAKK" -> DeltakelseStatus.TAKKET_JA
			"TILBUD" -> DeltakelseStatus.FATT_PLASS
			"VENTELISTE" -> DeltakelseStatus.VENTELISTE
			"IKKAKTUELL" -> DeltakelseStatus.IKKE_AKTUELL
			"AVSLAG" -> DeltakelseStatus.AVSLAG
			"IKKEM" -> DeltakelseStatus.IKKE_MOETT
			"NEITAKK" -> DeltakelseStatus.TAKKET_NEI
			else -> null
		}
	}

	fun toTittel(gjennomforingNavn: String, tiltakKode: String): String {
		val prefix = when (tiltakKode) {
			AMO -> "AMO-kurs: "
			GRUPPEAMO -> "Gruppe AMO: "
			ENKELAMO -> "Enkeltplass AMO: "
			else -> ""
		}
		return "$prefix$gjennomforingNavn"

	}

	fun convertToTiltaksaktivitet(
		deltaker: TiltakDeltaker,
		aktivitetId: UUID,
		personIdent: String,
		arrangorNavn: String?,
		gjennomforingNavn: String,
		tiltak: Tiltak,
		erNyAktivitet: Boolean,
	): Aktivitetskort {
		return Aktivitetskort(
			id = aktivitetId,
			personIdent = personIdent,
			tittel = toTittel(gjennomforingNavn, tiltak.kode),
			aktivitetStatus = toAktivitetStatus(deltaker.deltakerStatusKode),
			startDato = deltaker.datoFra,
			sluttDato = deltaker.datoTil,
			avtaltMedNav = true, // Arenatiltak er alltid Avtalt med NAV
			etiketter = listOfNotNull(
				toDeltakelseStatus(deltaker.deltakerStatusKode)
				?.let { deltakelseStatus -> Etikett(deltakelseStatus.toString()) }),
			beskrivelse = if (tiltak.kode == JOBBKLUBB) Beskrivelse(verdi = gjennomforingNavn) else null,
			endretTidspunkt = if (erNyAktivitet) deltaker.regDato else deltaker.modDato ?: throw IllegalArgumentException("Missing modDato"),
			endretAv = if (erNyAktivitet) Ident(ident = deltaker.regUser ?: throw IllegalArgumentException("Missing regUser"))
			           else Ident(ident = deltaker.modUser ?: throw IllegalArgumentException("Missing modUser")),
			detaljer = listOfNotNull(
				if (arrangorNavn != null) Attributt("Arrangør", arrangorNavn) else null,
				if (deltaker.prosentDeltid != null) Attributt("Deltakelse", "${deltaker.prosentDeltid}%") else null,
				if (deltaker.dagerPerUke != null) Attributt("Antall dager per uke", deltaker.dagerPerUke.toString()) else null,
			)
		)
	}
}
