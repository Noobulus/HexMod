package at.petrak.hexcasting.common.casting.operators.circles

import at.petrak.hexcasting.api.spell.ConstManaOperator
import at.petrak.hexcasting.api.spell.Operator
import at.petrak.hexcasting.api.spell.SpellDatum
import at.petrak.hexcasting.common.casting.CastingContext
import at.petrak.hexcasting.common.casting.mishaps.MishapNoSpellCircle
import net.minecraft.world.phys.Vec3

object OpImpetusPos : ConstManaOperator {
    override val argc = 0

    override fun execute(args: List<SpellDatum<*>>, ctx: CastingContext): List<SpellDatum<*>> {
        if (ctx.spellCircle == null)
            throw MishapNoSpellCircle()

        return Operator.spellListOf(Vec3.atCenterOf(ctx.spellCircle.impetusPos))
    }
}