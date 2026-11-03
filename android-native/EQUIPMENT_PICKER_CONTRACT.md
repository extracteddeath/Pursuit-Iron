# Native equipment picker — canonical grouping

Status: implemented on `architecture/android-compose-foundation`. This is a **presentation contract**, not an expansion of engine equipment rights or a source of physical load steps.

## Ordered groups

The Compose picker presents all 40 canonical production catalog IDs, in this exact order. **Hip Abduction must directly precede Hip Adduction.**

| Group | Display labels (canonical IDs) |
| --- | --- |
| Free weights | Dumbbells (`dumbbell`), Kettlebells (`kettlebell`), Bands (`bands`) |
| Bars | Barbell (`barbell`), EZ curl bar (`ezbar`), Trap / hex bar (`trapbar`), Safety squat bar (`safetybar`), Landmine (`landmine`) |
| Benches & stations | Flat bench (`bench`), Incline bench (`inclinebench`), Decline bench (`declinebench`), Preacher bench (`preacher`), Pull-up bar (`pullup`), Dip station (`dip`), Glute-ham developer (`ghd`), Reverse hyper (`reversehyper`) |
| Cables & general machines | Cable machine (`cable`), Smith machine (`smith`), Selectorized machines (`machine`), Assisted pull-up / dip (`assisted`) |
| Leg machines | Leg press (`legpress`), Hack squat (`hacksquat`), Belt squat (`beltsquat`), Leg extension (`legext`), Leg curl (`legcurl`), Calf machine (`calfmachine`) |
| Hip & glute machines | Hip thrust machine (`hipthrustmachine`), Glute kickback (`kickback`), Hip abduction (`abduction`), Hip adduction (`adduction`) |
| Upper-body machines | Chest press (`machinepress`), Pec deck (`pecdeck`), Shoulder press (`machineshoulder`), Lateral raise (`machinelatraise`), Row (`machinerow`), Pullover (`machinepullover`), Shrug (`machineshrug`) |
| Arm & core machines | Curl (`machinecurl`), Triceps extension (`machineext`), Crunch (`machinecrunch`) |

## Ownership and invariants

- **Engine remains authoritative.** `modules/training-domain/catalog.js:EQUIPMENT` is the append-only physical equipment ID source. The native bridge exports those IDs in `nativeProgramOptions`, not a duplicated 17-item list.
- **Presentation is ordered independently.** `NativeEquipmentCatalog.kt` owns the eight UI groups. It filters by the IDs the runtime offers; missing IDs must not silently become supported. Unknown *offered* IDs render in an Other supported equipment group until the UI mapping is updated.
- **Never reorder the source catalog.** Old issued program codes use source equipment indices. Group adjacency applies to the native picker only. In particular, do not move `adduction` next to `abduction` in the append-only source list.
- **Rack is a separately confirmed facility.** A new native profile that selects a barbell defaults to `no-rack` unless the athlete explicitly enables **Squat rack available**. The engine's existing `expandEquipment` only derives `rack` when `barbell` exists and `no-rack` is absent; exercises that require `rack` cannot appear in explicitly rackless programs. This internal `no-rack` sentinel is NOT an extra equipment checkbox and never claims a loading weight.
- **Legacy saved choices survive.** Original native programs can contain `rack` or omit `no-rack`; they restore with their historical rack semantics. A re-reviewed profile retains a previously positive `rack` if the user confirms it, but contradictory `rack` + `no-rack` flags are rejected. Rack flags are kept out of the 40 visible equipment choices.
- **Availability is not loading.** Selecting any machine, bar, or dumbbell says only that equipment can be used. Verified plates, increments, weights, Smith totals and specialty-machine per-exercise steps stay in `NativeGymInventoryCodec` and require explicit measured user input. There are no fabricated increments.
- **M3E interaction.** Use grouped headings and shared choice controls. Preserve selected states and accessibility labels through step changes and review; no extra per-item coach banners. The gym picker must remain usable in compact phone widths.
- **Kotlin engine rewrite parity.** When the new Kotlin engine owns program creation, it must accept the same canonical IDs and eligibility semantics, but it must **not** depend on the UI group order. Exercise catalog mapping and compatibility with historical saves need parity tests before engine cutover.

## Regression gates

`NativeEquipmentCatalogTest.kt` validates eight groups, forty unique entries, and adjacency; `NativeEquipmentSelectionPolicyTest.kt` tests group selection and legacy preservation; `NativeEquipmentFacilityContractTest.kt` proves explicitly absent rack and old rack restoration; `native-onboarding-test.mjs` compares the native mapping against the actual engine catalog, proves legacy rack restoration, and exercises program creation. Native UI compilation and canonical bridge workflows are the additional release gates. An APK remains outside this change.
