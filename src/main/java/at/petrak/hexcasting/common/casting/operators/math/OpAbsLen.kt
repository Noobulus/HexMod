package at.petrak.hexcasting.common.casting.operators.math

import at.petrak.hexcasting.api.spell.ConstManaOperator
import at.petrak.hexcasting.api.spell.Operator.Companion.spellListOf
import at.petrak.hexcasting.api.spell.SpellDatum
import at.petrak.hexcasting.common.casting.CastingContext
import kotlin.math.absoluteValue

object OpAbsLen : ConstManaOperator {
    override val argc: Int
        get() = 1

    override fun execute(args: List<SpellDatum<*>>, ctx: CastingContext): List<SpellDatum<*>> {
        val x = MathOpUtils.GetNumOrVec(args[0], 0)

        return spellListOf(
            x.map({ num -> num.absoluteValue }, { vec -> vec.length() })
        )
    }
}