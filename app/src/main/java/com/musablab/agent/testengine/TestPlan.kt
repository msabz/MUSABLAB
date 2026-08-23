package com.musablab.agent.testengine

import org.json.JSONArray
import org.json.JSONObject

sealed interface TestNode {
    data class Step(val id: String, val action: String, val args: JSONObject) : TestNode
    data class If(val id: String, val condition: Condition, val thenNodes: List<TestNode>, val elseNodes: List<TestNode>) : TestNode
    data class Retry(val id: String, val maxAttempts: Int, val condition: Condition?, val nodes: List<TestNode>) : TestNode
    data class Parallel(val id: String, val branches: List<List<TestNode>>) : TestNode
}

data class Condition(val type: String, val args: JSONObject)

data class TestPlan(
    val schema: Int,
    val name: String,
    val packageName: String,
    val nodes: List<TestNode>,
    val videoMode: String
) {
    companion object {
        fun parse(obj: JSONObject): TestPlan {
            require(obj.optInt("schema") == 1) { "unsupported test plan schema" }
            val pkg = obj.getString("package")
            require(pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { "invalid package" }
            return TestPlan(
                schema = 1,
                name = obj.optString("name", "unnamed"),
                packageName = pkg,
                nodes = parseNodes(obj.getJSONArray("steps")),
                videoMode = obj.optString("video", "off")
            )
        }

        private fun parseNodes(array: JSONArray): List<TestNode> = buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val id = o.optString("id", "n$i")
                when (o.optString("type", "step")) {
                    "step" -> add(TestNode.Step(id, o.getString("action"), o.optJSONObject("args") ?: JSONObject()))
                    "if" -> add(TestNode.If(
                        id,
                        parseCondition(o.getJSONObject("condition")),
                        parseNodes(o.optJSONArray("then") ?: JSONArray()),
                        parseNodes(o.optJSONArray("else") ?: JSONArray())
                    ))
                    "retry" -> add(TestNode.Retry(
                        id,
                        o.optInt("maxAttempts", 3).coerceIn(1, 20),
                        o.optJSONObject("until")?.let(::parseCondition),
                        parseNodes(o.getJSONArray("steps"))
                    ))
                    "parallel" -> {
                        val b = o.getJSONArray("branches")
                        add(TestNode.Parallel(id, buildList {
                            for (j in 0 until b.length()) add(parseNodes(b.getJSONArray(j)))
                        }))
                    }
                    else -> error("unknown node type at $id")
                }
            }
        }

        private fun parseCondition(o: JSONObject) = Condition(o.getString("type"), o.optJSONObject("args") ?: JSONObject())
    }
}
