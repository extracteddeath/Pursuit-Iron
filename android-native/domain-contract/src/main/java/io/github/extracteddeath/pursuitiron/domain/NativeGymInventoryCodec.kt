package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** Physical loading facts, never proposed loads or default gym equipment. */
data class NativeGymInventoryDraftResult(val json: String? = null, val error: String? = null) {
    val valid: Boolean get() = error == null
}

object NativeGymInventoryCodec {
    private val decimal = Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")
    private val allowed = setOf("unit", "barbell", "dumbbells", "cable", "machine", "smith", "exerciseOverrides")
    private fun keys(obj: JSONObject): Set<String> = obj.keys().asSequence().toSet()
    private fun exact(obj: JSONObject, expected: Set<String>) {
        require(keys(obj) == expected)
    }
    private fun number(obj: JSONObject, key: String, min: BigDecimal = BigDecimal.ZERO,
        max: BigDecimal = BigDecimal("99999.999")): BigDecimal {
        val item = obj.get(key)
        require(item is Number)
        val token = item.toString()
        require(decimal.matches(token))
        val value = BigDecimal(token)
        require(value >= min && value <= max)
        return value
    }
    private fun loadList(array: JSONArray) {
        require(array.length() in 1..250)
        val unique = mutableSetOf<BigDecimal>()
        repeat(array.length()) { i ->
            val value = number(JSONObject().put("weight", array.get(i)), "weight")
            require(unique.add(value))
        }
    }
    /** Strict accepted subset: missing families do not inherit factory defaults. */
    fun validate(raw: String, expectedUnit: String): String {
        require(expectedUnit == "lb" || expectedUnit == "kg")
        require(raw.toByteArray(Charsets.UTF_8).size <= 16384)
        val obj = JSONObject(raw)
        require(keys(obj).all { it in allowed } && obj.get("unit") == expectedUnit)
        var count = 0
        if (obj.has("barbell")) {
            val bar = obj.getJSONObject("barbell")
            exact(bar, setOf("barWeight", "platePairs"))
            number(bar, "barWeight", max = BigDecimal("500"))
            val plates = bar.getJSONArray("platePairs")
            require(plates.length() in 1..24)
            val unique = mutableSetOf<BigDecimal>()
            repeat(plates.length()) { i ->
                val pair = plates.getJSONObject(i)
                exact(pair, setOf("weight", "pairs"))
                require(unique.add(number(pair, "weight", BigDecimal("0.001"), BigDecimal("100"))))
                val pairs = number(pair, "pairs", BigDecimal.ONE, BigDecimal("50"))
                require(pairs.scale() <= 0)
            }
            count++
        }
        if (obj.has("dumbbells")) {
            val db = obj.getJSONObject("dumbbells")
            exact(db, setOf("availablePerHand"))
            loadList(db.getJSONArray("availablePerHand"))
            count++
        }
        for (family in listOf("cable", "machine")) if (obj.has(family)) {
            val range = obj.getJSONObject(family)
            exact(range, setOf("minimum", "increment", "maximum"))
            val min = number(range, "minimum")
            val inc = number(range, "increment", BigDecimal("0.001"))
            val max = number(range, "maximum")
            require(inc > BigDecimal.ZERO && max >= min)
            count++
        }
        if (obj.has("smith")) {
            val smith = obj.getJSONObject("smith")
            exact(smith, setOf("availableLoads"))
            loadList(smith.getJSONArray("availableLoads"))
            count++
        }
        if (obj.has("exerciseOverrides")) exact(obj.getJSONObject("exerciseOverrides"), emptySet())
        require(count > 0)
        return obj.toString()
    }

    /**
     * Blank is explicitly unknown. No form field is prefilled with hypothetical
     * plate or stack availability. Incomplete sets are errors, not guessed.
     */
    fun fromDraft(fields: Map<String, String>, unit: String): NativeGymInventoryDraftResult = try {
        val nonblank = fields.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        if (nonblank.isEmpty()) NativeGymInventoryDraftResult()
        else {
            val obj = JSONObject().put("unit", unit)
            fun numeric(token: String): BigDecimal {
                require(decimal.matches(token.trim()))
                return BigDecimal(token.trim())
            }
            fun loads(raw: String): JSONArray {
                val values = raw.split(",").map { numeric(it) }
                return JSONArray().apply { values.forEach { put(it) } }
            }
            if ("bar" in nonblank || "plates" in nonblank) {
                require("bar" in nonblank && "plates" in nonblank)
                val plates = JSONArray()
                nonblank.getValue("plates").split(",").forEach { entry ->
                    val pair = entry.trim().split(Regex("[xX]"))
                    require(pair.size == 2)
                    plates.put(JSONObject().put("weight", numeric(pair[0]))
                        .put("pairs", numeric(pair[1])))
                }
                obj.put("barbell", JSONObject().put("barWeight", numeric(nonblank.getValue("bar")))
                    .put("platePairs", plates))
            }
            if ("dumbbells" in nonblank) obj.put("dumbbells", JSONObject()
                .put("availablePerHand", loads(nonblank.getValue("dumbbells"))))
            for (family in listOf("cable", "machine")) if (family in nonblank) {
                val values = nonblank.getValue(family).split(",").map { numeric(it) }
                require(values.size == 3)
                obj.put(family, JSONObject().put("minimum", values[0])
                    .put("increment", values[1]).put("maximum", values[2]))
            }
            if ("smith" in nonblank) obj.put("smith", JSONObject()
                .put("availableLoads", loads(nonblank.getValue("smith"))))
            NativeGymInventoryDraftResult(json = validate(obj.toString(), unit))
        }
    } catch (_: Exception) {
        NativeGymInventoryDraftResult(error = "Check actual weights, counts and units. Use commas between entries; no defaults are assumed.")
    }

    fun toDraft(raw: String?): Map<String, String> {
        if (raw == null) return emptyMap()
        val obj = JSONObject(raw)
        val draft = mutableMapOf<String, String>()
        fun list(array: JSONArray) = (0 until array.length()).joinToString(", ") { array.get(it).toString() }
        if (obj.has("barbell")) {
            val bar = obj.getJSONObject("barbell")
            draft["bar"] = bar.get("barWeight").toString()
            val plates = bar.getJSONArray("platePairs")
            draft["plates"] = (0 until plates.length()).joinToString(", ") {
                val item = plates.getJSONObject(it)
                "${item.get("weight")}x${item.get("pairs")}"
            }
        }
        if (obj.has("dumbbells")) draft["dumbbells"] =
            list(obj.getJSONObject("dumbbells").getJSONArray("availablePerHand"))
        for (family in listOf("cable", "machine")) if (obj.has(family)) {
            val r = obj.getJSONObject(family)
            draft[family] = "${r.get("minimum")}, ${r.get("increment")}, ${r.get("maximum")}"
        }
        if (obj.has("smith")) draft["smith"] = list(obj.getJSONObject("smith").getJSONArray("availableLoads"))
        return draft
    }
}
