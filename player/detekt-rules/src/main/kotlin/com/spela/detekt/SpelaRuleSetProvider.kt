package com.spela.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class SpelaRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("spela")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        listOf(::ComponentOuterSpacingRule),
    )
}
