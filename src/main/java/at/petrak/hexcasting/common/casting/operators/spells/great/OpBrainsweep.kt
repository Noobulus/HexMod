package at.petrak.hexcasting.common.casting.operators.spells.great

import at.petrak.hexcasting.api.spell.Operator.Companion.getChecked
import at.petrak.hexcasting.api.spell.ParticleSpray
import at.petrak.hexcasting.api.spell.RenderedSpell
import at.petrak.hexcasting.api.spell.SpellDatum
import at.petrak.hexcasting.api.spell.SpellOperator
import at.petrak.hexcasting.common.casting.CastingContext
import at.petrak.hexcasting.common.casting.mishaps.MishapAlreadyBrainswept
import at.petrak.hexcasting.common.casting.mishaps.MishapBadBrainsweep
import at.petrak.hexcasting.common.misc.Brainsweeping
import at.petrak.hexcasting.common.recipe.BrainsweepRecipe
import at.petrak.hexcasting.common.recipe.HexRecipeSerializers
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.phys.Vec3

object OpBrainsweep : SpellOperator {
    override val argc = 2

    override val isGreat = true

    override fun execute(
        args: List<SpellDatum<*>>,
        ctx: CastingContext
    ): Triple<RenderedSpell, Int, List<ParticleSpray>> {
        val sacrifice = args.getChecked<Villager>(0)
        val pos = args.getChecked<Vec3>(1)
        ctx.assertVecInRange(pos)
        ctx.assertEntityInRange(sacrifice)

        if (Brainsweeping.isBrainswept(sacrifice))
            throw MishapAlreadyBrainswept(sacrifice)

        val bpos = BlockPos(pos)
        val state = ctx.world.getBlockState(bpos)

        val recman = ctx.world.recipeManager
        val recipes = recman.getAllRecipesFor(HexRecipeSerializers.BRAINSWEEP_TYPE)
        val recipe = recipes.find { it.matches(state, sacrifice) }
            ?: throw MishapBadBrainsweep(sacrifice, bpos)

        return Triple(
            Spell(bpos, sacrifice, recipe),
            1_000_000,
            listOf(ParticleSpray.Cloud(sacrifice.position(), 1.0), ParticleSpray.Burst(Vec3.atCenterOf(bpos), 0.3, 100))
        )
    }

    private data class Spell(val pos: BlockPos, val sacrifice: Villager, val recipe: BrainsweepRecipe) : RenderedSpell {
        override fun cast(ctx: CastingContext) {
            ctx.world.setBlockAndUpdate(pos, recipe.result)
            Brainsweeping.brainsweep(sacrifice)

            ctx.world.playSound(null, sacrifice, SoundEvents.VILLAGER_DEATH, SoundSource.AMBIENT, 0.8f, 1f)
            ctx.world.playSound(null, sacrifice, SoundEvents.PLAYER_LEVELUP, SoundSource.AMBIENT, 0.5f, 0.8f)
        }
    }


}