package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachExerciseCatalogDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private val db=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30"),n("35")))
    private val cable=VerifiedLoadStock.Stack(LoadUnit.LB,
        LoadBasis.CABLE_STACK,n("10"),n("5"),n("100"))
    private val gym=CatalogGym(mapOf("db-rack" to db,"cable-a" to cable),
        setOf("rope","straight-bar"))
    private fun spec(
        id:String,muscle:TrainingMuscle,
        pattern:ComposerPattern=ComposerPattern.ISOLATION,
        role:ComposerRole=ComposerRole.SECONDARY,
        equipmentId:String?="db-rack",
        basis:LoadBasis?=LoadBasis.DUMBBELL_PER_HAND,
        attachment:String?=null,
        creditStatus:CatalogCreditStatus=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
        review:String?="catalog-v1"
    )=CatalogMovement(id,id,"default",attachment,pattern,role,
        setOf(ComposerFocus.FULL_BODY),equipmentId,basis,
        listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
        creditStatus,review)
    private val press=spec("press",TrainingMuscle.CHEST,
        ComposerPattern.HORIZONTAL_PRESS,ComposerRole.PRIMARY)
    private val row=spec("row",TrainingMuscle.LATS,ComposerPattern.HORIZONTAL_PULL)
    private val squat=spec("squat",TrainingMuscle.QUADS,ComposerPattern.SQUAT)
    private val catalog=listOf(press,row,squat)
    private fun choice(id:String,load:BigDecimal?=n("30"))=
        CatalogExerciseChoice(id,load,90,120,2,4,3,1,1,4)
    private val choices=catalog.map {choice(it.exerciseId)}
    private fun template(intents:List<ComposerSessionIntent> =emptyList(),
        exercises:List<ComposerExerciseOption> =emptyList()):ComposerRequest {
        val bounds=TrainingMuscle.entries.map {muscle->
            if(muscle in setOf(TrainingMuscle.CHEST,TrainingMuscle.LATS,
                TrainingMuscle.QUADS))
                DeclaredMuscleDoseBounds(muscle,n("2"),n("5"),n("8"),1)
            else DeclaredMuscleDoseBounds(muscle,n("0"),n("0"),n("0"),0)
        }
        return ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
            listOf(0,2,4).map {
                ComposerDay(it,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,90,null,60))
            },bounds,exercises,4,1,intents)
    }
    private fun request(
        movements:List<CatalogMovement> =catalog,
        selections:List<CatalogExerciseChoice> =choices,
        gym:CatalogGym=this.gym,
        template:ComposerRequest=template()
    )=CatalogWeekRequest(template,movements,selections,gym)

    @Test fun traceableCatalogProducesActualComposedWeekWithoutCreatingTargets() {
        val original=request()
        val result=CoachExerciseCatalogDomain.review(original)
        assertEquals(CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW,
            result.status)
        assertTrue(result.issues.isEmpty())
        assertNotNull(result.composedRequest)
        assertNotNull(result.draft)
        assertTrue(checkNotNull(result.draft).audit.satisfiesDeclaredHardConstraints)
        assertEquals(3,checkNotNull(result.composedRequest).exercises.size)
        assertTrue(result.draft!!.visits.isNotEmpty())
        assertEquals(emptyList<ComposerExerciseOption>(),original.template.exercises)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayOverwriteAuthoredProgram)
    }

    @Test fun unknownOrUnreviewedMuscleCreditCannotEnterComposer() {
        val unknown=CoachExerciseCatalogDomain.review(request(
            selections=listOf(choice("unknown"),choice("row"),choice("squat"))))
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,unknown.status)
        assertTrue(unknown.issues.any {it.kind==CatalogIssueType.UNKNOWN_EXERCISE})
        assertNull(unknown.draft)
        val pending=press.copy(creditStatus=CatalogCreditStatus.UNREVIEWED)
        val blocked=CoachExerciseCatalogDomain.review(request(
            movements=listOf(pending,row,squat)))
        assertTrue(blocked.issues.any {it.kind==CatalogIssueType.CREDIT_REVIEW_MISSING})
        assertNull(blocked.composedRequest)
        val noReference=press.copy(creditReviewReference=null)
        val untraceable=CoachExerciseCatalogDomain.review(request(
            movements=listOf(noReference,row,squat)))
        assertTrue(untraceable.issues.any {
            it.kind==CatalogIssueType.CREDIT_REVIEW_MISSING
        })
    }

    @Test fun gymInventoryMustMatchBothImplementAndLoadBasis() {
        val unconfirmed=CoachExerciseCatalogDomain.review(request(
            gym=CatalogGym(mapOf("cable-a" to cable),gym.availableAttachmentIds)))
        assertTrue(unconfirmed.issues.any {
            it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
        })
        assertNull(unconfirmed.draft)
        val incompatible=CoachExerciseCatalogDomain.review(request(
            gym=CatalogGym(mapOf("db-rack" to cable,"cable-a" to cable),
                gym.availableAttachmentIds)))
        assertTrue(incompatible.issues.any {
            it.kind==CatalogIssueType.EQUIPMENT_BASIS_MISMATCH
        })
        assertNull(incompatible.draft)
    }

    @Test fun impossibleDumbbellIncrementIsNeverRoundedOrUsed() {
        val modified=choices.map {
            if(it.exerciseId=="press")it.copy(selectedLoad=n("32.5"))else it
        }
        val r=CoachExerciseCatalogDomain.review(request(selections=modified))
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,r.status)
        assertTrue(r.issues.any {it.kind==CatalogIssueType.LOAD_UNVERIFIED})
        assertNull(r.composedRequest)
        assertEquals(n("32.5"),modified.first().selectedLoad)
    }

    @Test fun ropeAndStraightBarAreNotInterchangeableAttachments() {
        val rope=spec("pushdown-rope",TrainingMuscle.TRICEPS,
            equipmentId="cable-a",basis=LoadBasis.CABLE_STACK,attachment="rope")
            .copy(familyId="pushdown",variantId="rope")
        val straight=rope.copy(exerciseId="pushdown-straight",
            variantId="straight",attachmentId="straight-bar")
        val scarce=CatalogGym(gym.confirmedStock,setOf("rope"))
        val selected=listOf(choice("press"),choice("row"),choice("squat"),
            choice("pushdown-straight",n("30")))
        val blocked=CoachExerciseCatalogDomain.review(request(
            movements=catalog+rope+straight,selections=selected,gym=scarce))
        assertTrue(blocked.issues.any {
            it.exerciseId=="pushdown-straight" &&
                it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE
        })
        assertNull(blocked.draft)
        val valid=CoachExerciseCatalogDomain.review(request(
            movements=catalog+rope+straight,
            selections=selected.map {
                if(it.exerciseId=="pushdown-straight")
                    it.copy(exerciseId="pushdown-rope")else it
            },gym=scarce))
        assertTrue(valid.issues.isEmpty())
        assertNotNull(valid.draft)
        assertEquals(listOf("press","row","squat","pushdown-rope"),
            valid.requestedExerciseIds)
    }

    @Test fun bodyweightMeansNoNumericLoadAndNoImaginaryDumbbellZero() {
        val body=press.copy(equipmentId=null,expectedLoadBasis=null)
        val validChoices=choices.map {
            if(it.exerciseId=="press")it.copy(selectedLoad=null)else it
        }
        val result=CoachExerciseCatalogDomain.review(request(
            movements=listOf(body,row,squat),selections=validChoices))
        assertTrue(result.issues.isEmpty())
        val equipment=checkNotNull(result.composedRequest).exercises.first()
            .equipment
        assertEquals(ComposerEquipment.Bodyweight,equipment)
        val wrong=CoachExerciseCatalogDomain.review(request(
            movements=listOf(body,row,squat)))
        assertTrue(wrong.issues.any {
            it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
        })
        assertNull(wrong.draft)
    }

    @Test fun missingOrIncompatiblePrimaryCannotBeSilentlySubstituted() {
        val intent=ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,
            "press")
        val missing=CoachExerciseCatalogDomain.review(request(
            selections=choices.filterNot {it.exerciseId=="press"},
            template=template(listOf(intent))))
        assertTrue(missing.issues.any {
            it.kind==CatalogIssueType.MISSING_PRIMARY_MOVEMENT
        })
        assertNull(missing.draft)
        val valid=CoachExerciseCatalogDomain.review(request(
            template=template(listOf(intent))))
        assertTrue(valid.issues.isEmpty())
        assertEquals("press",valid.draft!!.sessions.first().primaryExerciseId)
    }

    @Test fun userSuppliedUnverifiedOptionsCannotBypassCatalogReview() {
        val fake=ComposerExerciseOption("fake",ComposerPattern.HORIZONTAL_PRESS,
            setOf(ComposerFocus.FULL_BODY),ComposerEquipment.Bodyweight,
            listOf(DeclaredMuscleCredit(TrainingMuscle.CHEST,
                MuscleCreditRole.DIRECT,n("1"))),
            90,90,2,4,3,0,1)
        assertThrows(IllegalArgumentException::class.java) {
            CoachExerciseCatalogDomain.review(request(
                template=template(exercises=listOf(fake))))
        }
    }

    @Test fun separateBicepsBrachialisAndWristMuscleAccountsArePreserved() {
        val biceps=spec("curl-biceps",TrainingMuscle.BICEPS)
        val brachialis=spec("curl-brachialis",TrainingMuscle.BRACHIALIS)
        val wrist=spec("wrist-flex",TrainingMuscle.WRIST_FLEXORS)
        val wristExt=spec("wrist-extend",TrainingMuscle.WRIST_EXTENSORS)
        val r=CoachExerciseCatalogDomain.review(request(
            movements=catalog+listOf(biceps,brachialis,wrist,wristExt),
            selections=choices+listOf(biceps,brachialis,wrist,wristExt).map {
                choice(it.exerciseId)
            }))
        assertTrue(r.issues.isEmpty())
        val byId=r.composedRequest!!.exercises.associateBy {it.exerciseId}
        assertEquals(TrainingMuscle.BICEPS,byId.getValue("curl-biceps")
            .credits.single().muscle)
        assertEquals(TrainingMuscle.BRACHIALIS,byId.getValue("curl-brachialis")
            .credits.single().muscle)
        assertEquals(TrainingMuscle.WRIST_FLEXORS,byId.getValue("wrist-flex")
            .credits.single().muscle)
        assertEquals(TrainingMuscle.WRIST_EXTENSORS,byId.getValue("wrist-extend")
            .credits.single().muscle)
        assertFalse(r.mayIssueWorkout)
    }

    @Test fun invalidCatalogProvenanceAndDuplicateVariantsFailClosed() {
        val duplicate=press.copy(exerciseId="another-id")
        val invalid=listOf(
            request(movements=catalog+duplicate),
            request(movements=catalog+press),
            request(movements=catalog.map {
                if(it.exerciseId=="press")it.copy(credits=listOf(
                    DeclaredMuscleCredit(TrainingMuscle.CHEST,
                        MuscleCreditRole.DIRECT,n("0"))))else it
            }),
            request(movements=catalog.map {
                if(it.exerciseId=="press")it.copy(familyId="not valid")else it
            }),
            request(movements=catalog.map {
                if(it.exerciseId=="press")it.copy(
                    equipmentId=null,expectedLoadBasis=LoadBasis.CABLE_STACK)
                else it
            })
        )
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) {
                CoachExerciseCatalogDomain.review(it)
            }
        }
    }

    @Test fun composedResultsHaveStableIdsAndRepeatableAudit() {
        val original=request()
        val a=CoachExerciseCatalogDomain.review(original)
        val b=CoachExerciseCatalogDomain.review(original)
        assertEquals(a,b)
        val ids=a.draft!!.week.exposures.map {it.setKey}
        assertEquals(ids.size,ids.distinct().size)
        val unresolved=CoachExerciseCatalogDomain.review(request(
            selections=listOf(choice("missing-id"),choice("row"),choice("squat"))))
        assertTrue(unresolved.issues.isNotEmpty())
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val mutable=unresolved.issues as MutableList<CatalogIssue>
            mutable.add(CatalogIssue("invented",CatalogIssueType.UNKNOWN_EXERCISE,
                "invalid"))
        }
    }
}
