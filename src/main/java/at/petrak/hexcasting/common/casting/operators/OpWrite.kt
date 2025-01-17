package at.petrak.hexcasting.common.casting.operators

import at.petrak.hexcasting.api.spell.ParticleSpray
import at.petrak.hexcasting.api.spell.RenderedSpell
import at.petrak.hexcasting.api.spell.SpellDatum
import at.petrak.hexcasting.api.spell.SpellOperator
import at.petrak.hexcasting.common.blocks.circles.BlockEntitySlate
import at.petrak.hexcasting.common.casting.CastingContext
import at.petrak.hexcasting.common.casting.mishaps.MishapBadOffhandItem
import at.petrak.hexcasting.common.casting.mishaps.MishapOthersName
import at.petrak.hexcasting.common.items.HexItems
import at.petrak.hexcasting.common.items.ItemDataHolder
import at.petrak.hexcasting.common.items.ItemScroll
import at.petrak.hexcasting.hexmath.HexPattern
import net.minecraft.world.entity.player.Player

// we make this a spell cause imo it's a little ... anticlimactic for it to just make no noise
object OpWrite : SpellOperator {
    override val argc = 1
    override fun execute(
        args: List<SpellDatum<*>>,
        ctx: CastingContext
    ): Triple<RenderedSpell, Int, List<ParticleSpray>> {
        val handStack = ctx.caster.getItemInHand(ctx.otherHand)
        val handItem = handStack.item
        val tag = handStack.orCreateTag
        val datum = args[0]

        val canWrite = if (handItem is ItemDataHolder) {
            handItem.canWrite(tag, datum)
        } else if (datum.payload is HexPattern) {
            if (handStack.`is`(HexItems.SCROLL.get()) && !tag.contains(ItemScroll.TAG_PATTERN)) {
                true
            } else if (handStack.`is`(HexItems.SLATE.get())) {
                val hasBET = tag.contains("BlockEntityTag")
                if (hasBET) {
                    tag.getCompound("BlockEntityTag").contains(BlockEntitySlate.TAG_PATTERN)
                } else {
                    true
                }
            } else {
                false
            }
        } else {
            false
        }
        if (!canWrite)
            throw MishapBadOffhandItem.of(handStack, "iota.write")

        val trueName = MishapOthersName.getTrueNameFromDatum(datum, ctx.caster)
        if (trueName != null)
            throw MishapOthersName(trueName)

        return Triple(
            Spell(datum),
            0,
            listOf()
        )
    }

    private data class Spell(val datum: SpellDatum<*>) : RenderedSpell {
        override fun cast(ctx: CastingContext) {
            val handStack = ctx.caster.getItemInHand(ctx.otherHand)
            val handItem = handStack.item
            val tag = handStack.orCreateTag
            if (handItem is ItemDataHolder) {
                handItem.writeDatum(tag, datum)
            } else if (handItem == HexItems.SCROLL.get() && !tag.contains(ItemScroll.TAG_PATTERN) && datum.payload is HexPattern) {
                tag.put(ItemScroll.TAG_PATTERN, datum.payload.serializeToNBT())
            } else if (handItem == HexItems.SLATE.get() && datum.payload is HexPattern) {
                val bet = tag.getCompound("BlockEntityTag")
                bet.put(BlockEntitySlate.TAG_PATTERN, datum.payload.serializeToNBT())
                // Just in case it's brand new
                tag.put("BlockEntityTag", bet)
            } else {
                // Fuck
            }
        }

    }
}
