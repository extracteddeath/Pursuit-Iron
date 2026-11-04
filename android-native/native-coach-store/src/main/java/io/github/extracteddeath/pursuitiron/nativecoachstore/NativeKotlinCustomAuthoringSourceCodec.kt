package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.util.Collections

/**
 * Durable INPUTS to Kotlin custom-cycle authoring, not a program grant.
 * Schema is deliberately explicit and versioned. Decoding re-runs native
 * athlete + gym review; arbitrary Java serialization and JS data are banned.
 */
data class NativeCustomAuthoringSnapshot(
    val ownerKey: String,
    val athleteId: String,
    val revision: Long,
    val setup: AthleteSetupReview,
    val catalog: List<CatalogMovement>
) {
    val mayIssueWorkout: Boolean get() = false
}

internal object NativeKotlinCustomAuthoringSourceCodec {
    private const val MAGIC = 0x50494353 // PICS
    private const val VERSION = 1
    const val MAX_BYTES = 512_000
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun DataOutputStream.word(value: String) = writeUTF(value)
    private fun DataOutputStream.decimal(value: BigDecimal) =
        word(value.toPlainString())
    private fun DataInputStream.decimal(): BigDecimal = BigDecimal(readUTF())
    private fun DataOutputStream.enum(value: Enum<*>) = word(value.name)
    private inline fun <reified T : Enum<T>> DataInputStream.enum(): T {
        val raw = readUTF()
        return enumValues<T>().firstOrNull { it.name == raw }
            ?: throw IOException("Unknown authoring enum")
    }
    private fun DataOutputStream.optional(value: String?) {
        writeBoolean(value != null)
        if (value != null) word(value)
    }
    private fun DataInputStream.optional(): String? =
        if (readBoolean()) readUTF() else null
    private fun <T> DataOutputStream.items(values: Collection<T>, writer: (T) -> Unit) {
        writeInt(values.size); values.forEach(writer)
    }
    private fun <T> DataInputStream.items(limit: Int, reader: () -> T): List<T> {
        val size = readInt()
        if (size !in 0..limit) throw IOException("Oversized custom authoring collection")
        return List(size) { reader() }
    }
    private fun DataOutputStream.words(values: Set<String>) {
        items(values.sorted()) { word(it) }
    }
    private fun DataInputStream.words(limit: Int): Set<String> =
        items(limit) { readUTF() }.also {
            if (it.distinct().size != it.size) throw IOException("Duplicate authoring IDs")
        }.toSet()
    private fun DataOutputStream.weekdays(values: Set<Int>) {
        items(values.sorted()) { writeInt(it) }
    }
    private fun DataInputStream.weekdays(): Set<Int> =
        items(7) { readInt() }.also {
            if (it.distinct().size != it.size) throw IOException("Duplicate weekdays")
        }.toSet()
    private fun DataOutputStream.muscles(values: Set<TrainingMuscle>) {
        items(values.sortedBy { it.name }) { enum(it) }
    }
    private fun DataInputStream.muscles(): Set<TrainingMuscle> =
        items(TrainingMuscle.entries.size) { enum<TrainingMuscle>() }.also {
            if (it.distinct().size != it.size) throw IOException("Duplicate muscles")
        }.toSet()

    private fun DataOutputStream.stock(stock: VerifiedLoadStock) {
        when (stock) {
            is VerifiedLoadStock.Totals -> {
                writeByte(1);enum(stock.unit);enum(stock.basis)
                items(stock.loads) { decimal(it) }
            }
            is VerifiedLoadStock.Barbell -> {
                writeByte(2);enum(stock.unit);decimal(stock.barWeight)
                items(stock.pairedPlates.entries.sortedBy { it.key }) {
                    decimal(it.key);writeInt(it.value)
                }
            }
            is VerifiedLoadStock.Stack -> {
                writeByte(3);enum(stock.unit);enum(stock.basis)
                decimal(stock.minimum);decimal(stock.increment)
                decimal(stock.maximum)
            }
        }
    }
    private fun DataInputStream.stock(): VerifiedLoadStock = when (readUnsignedByte()) {
        1 -> VerifiedLoadStock.Totals(enum<LoadUnit>(),enum<LoadBasis>(),
            items(1024) { decimal() })
        2 -> {
            val unit=enum<LoadUnit>();val bar=decimal()
            val values=items(32) { decimal() to readInt() }
            if (values.map { it.first }.distinct().size!=values.size)
                throw IOException("Duplicate plate denomination")
            VerifiedLoadStock.Barbell(unit,bar,values.toMap())
        }
        3 -> VerifiedLoadStock.Stack(enum<LoadUnit>(),enum<LoadBasis>(),
            decimal(),decimal(),decimal())
        else -> throw IOException("Unknown gym stock")
    }
    private fun DataOutputStream.setup(athlete: ReviewedAthleteSetup) {
        word(athlete.profileRevision);word(athlete.athleteProfileId)
        enum(athlete.goal);enum(athlete.experience);enum(athlete.split)
        weekdays(athlete.trainingWeekdays)
        word(athlete.timezoneId);enum(athlete.sessionTimeBucket)
        when (val limit=athlete.hardSessionLimit) {
            HardSessionLimit.NO_HARD_LIMIT -> writeByte(0)
            is HardSessionLimit.Minutes -> {writeByte(1);writeInt(limit.maximum)}
        }
        word(athlete.gym.gymId);enum(athlete.gym.unit)
        items(athlete.gym.confirmedStock.entries.sortedBy { it.key }) {
            word(it.key);stock(it.value)
        }
        words(athlete.gym.availableFacilities)
        words(athlete.gym.availableAttachments)
        writeBoolean(athlete.allowBodyweight)
        writeBoolean(athlete.allowSupersets)
        writeBoolean(athlete.allowTricepsOnLower)
        writeInt(athlete.maxBarbellExercisesPerDay)
        muscles(athlete.focusedMuscles);muscles(athlete.reducedMuscles)
        words(athlete.excludedExerciseIds)
        weekdays(athlete.lockedTrainingDays)
        weekdays(athlete.hardTrainingDays)
        weekdays(athlete.longTrainingDays)
        weekdays(athlete.declaredRestDays)
    }
    private fun DataInputStream.setup(): AthleteSetupReview {
        val profileRevision=readUTF();val athleteId=readUTF()
        val goal=enum<AthleteTrainingGoal>()
        val experience=enum<AthleteExperience>()
        val split=enum<ComposerSplit>()
        val trainingDays=weekdays()
        val timezone=readUTF()
        val bucket=enum<SessionTimeBucket>()
        val hardLimit=when (readUnsignedByte()) {
            0 -> HardSessionLimit.NO_HARD_LIMIT
            1 -> HardSessionLimit.Minutes(readInt())
            else -> throw IOException("Unknown session time policy")
        }
        val gymId=readUTF();val unit=enum<LoadUnit>()
        val gymValues=items(128) { readUTF() to stock() }
        if (gymValues.map { it.first }.distinct().size!=gymValues.size)
            throw IOException("Duplicate gym equipment")
        val facilities=words(128)
        val attachments=words(128)
        val allowBodyweight=readBoolean()
        val allowSupersets=readBoolean()
        val allowTriceps=readBoolean()
        val maximumBarbells=readInt()
        val focused=muscles();val reduced=muscles()
        val excluded=words(1024)
        val locked=weekdays();val hard=weekdays()
        val long=weekdays();val rest=weekdays()
        return CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision=profileRevision,athleteProfileId=athleteId,
            goal=goal,experience=experience,split=split,
            trainingWeekdays=trainingDays,timezoneId=timezone,
            sessionTimeBucket=bucket,hardSessionLimit=hardLimit,
            gym=GymProfileDraft(gymId,unit,gymValues.toMap(),
                facilities,attachments),
            allowBodyweight=allowBodyweight,allowSupersets=allowSupersets,
            allowTricepsOnLower=allowTriceps,
            maxBarbellExercisesPerDay=maximumBarbells,
            focusedMuscles=focused,reducedMuscles=reduced,
            excludedExerciseIds=excluded,
            lockedTrainingDays=locked,hardTrainingDays=hard,
            longTrainingDays=long,declaredRestDays=rest))
    }

    private fun DataOutputStream.movement(move: CatalogMovement) {
        word(move.exerciseId);word(move.familyId);word(move.variantId)
        optional(move.attachmentId);enum(move.pattern);enum(move.role)
        items(move.permittedFocus.sortedBy { it.name }) { enum(it) }
        optional(move.equipmentId)
        writeBoolean(move.expectedLoadBasis != null)
        move.expectedLoadBasis?.let { enum(it) }
        items(move.credits) {
            enum(it.muscle);enum(it.role);decimal(it.fractionalSet)
        }
        enum(move.creditStatus);optional(move.creditReviewReference)
        words(move.requiredFacilityIds)
        writeBoolean(move.sourcePracticalReps != null)
        move.sourcePracticalReps?.let {
            writeInt(it.minimum);writeInt(it.maximum)
        }
        optional(move.recoveryGroupId)
        writeInt(move.minimumRecoveryGroupDayGap)
    }
    private fun DataInputStream.movement(): CatalogMovement {
        val exerciseId=readUTF();val familyId=readUTF();val variantId=readUTF()
        val attachment=optional()
        val pattern=enum<ComposerPattern>();val role=enum<ComposerRole>()
        val focus=items(ComposerFocus.entries.size) { enum<ComposerFocus>() }
        if (focus.distinct().size!=focus.size) throw IOException("Duplicate focus")
        val equipment=optional()
        val basis=if (readBoolean()) enum<LoadBasis>() else null
        val credits=items(TrainingMuscle.entries.size) {
            DeclaredMuscleCredit(enum<TrainingMuscle>(),
                enum<MuscleCreditRole>(),decimal())
        }
        val status=enum<CatalogCreditStatus>()
        val reference=optional()
        val facilities=words(8)
        val reps=if (readBoolean()) RepBand(readInt(),readInt()) else null
        val recovery=optional()
        val gap=readInt()
        return CatalogMovement(exerciseId,familyId,variantId,
            attachment,pattern,role,focus.toSet(),equipment,basis,
            credits,status,reference,facilities,reps,recovery,gap)
    }

    /** Retain explicit source identity; this is NOT authentication itself. */
    fun encode(record: NativeCustomAuthoringSnapshot): ByteArray {
        require(id.matches(record.ownerKey) && id.matches(record.athleteId))
        require(record.revision in 1..9_007_199_254_740_991L)
        val athlete=record.setup.reviewed
        require(record.setup.status == AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW &&
            athlete!=null && athlete.athleteProfileId==record.athleteId)
        require(record.catalog.size in 1..256)
        require(record.catalog.map { it.exerciseId }.distinct().size ==
            record.catalog.size)
        require(record.catalog.map { it.familyId to it.variantId }.distinct().size ==
            record.catalog.size)
        require(record.catalog.all {it.exerciseId.matches(id) &&
            it.familyId.matches(id) && it.variantId.matches(id)})
        require(NativeKotlinGymCatalogEditingDomain.replaceCuratedCatalog(
            record,record.revision,record.catalog) is NativeGymCatalogEdit.Proposed) {
            "Catalog must carry reviewed, bounded variant identities and credits"
        }
        // Revalidate the entire athlete, including physical inventory, on write.
        val output=ByteArrayOutputStream()
        DataOutputStream(output).use { o ->
            with(o) {
                writeInt(MAGIC);writeInt(VERSION)
                word(record.ownerKey);word(record.athleteId)
                writeLong(record.revision);setup(athlete)
                items(record.catalog) { movement(it) }
            }
        }
        val bytes=output.toByteArray()
        require(bytes.size<=MAX_BYTES)
        return bytes
    }

    fun decode(bytes: ByteArray): NativeCustomAuthoringSnapshot {
        if (bytes.size !in 16..MAX_BYTES)
            throw IOException("Invalid source snapshot length")
        try {
            val decoded=DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                with(data) {
                    if (readInt()!=MAGIC || readInt()!=VERSION)
                        throw IOException("Unknown native authoring version")
                    val owner=readUTF();val athleteId=readUTF()
                    val revision=readLong();val setup=setup()
                    val catalog=items(256) { movement() }
                    if (available()!=0) throw IOException("Trailing source bytes")
                    NativeCustomAuthoringSnapshot(owner,athleteId,
                        revision,setup,Collections.unmodifiableList(catalog))
                }
            }
            // Require valid review after cold restoration, never trust flags.
            encode(decoded)
            return decoded
        } catch (error: Exception) {
            throw IOException("Invalid native authoring inputs",error)
        }
    }
}
