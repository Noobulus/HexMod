package at.petrak.hexcasting.common.casting.operators.spells

import at.petrak.hexcasting.api.spell.Operator.Companion.getChecked
import at.petrak.hexcasting.api.spell.ParticleSpray
import at.petrak.hexcasting.api.spell.RenderedSpell
import at.petrak.hexcasting.api.spell.SpellDatum
import at.petrak.hexcasting.api.spell.SpellOperator
import at.petrak.hexcasting.common.casting.CastingContext
import at.petrak.hexcasting.common.casting.ManaHelper
import at.petrak.hexcasting.common.casting.mishaps.MishapBadOffhandItem
import at.petrak.hexcasting.common.casting.mishaps.MishapInvalidIota
import at.petrak.hexcasting.common.items.magic.ItemPackagedSpell
import at.petrak.hexcasting.hexmath.HexPattern
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.world.entity.item.ItemEntity

class OpMakePackagedSpell<T : ItemPackagedSpell>(val itemType: T, val cost: Int) : SpellOperator {
    override val argc = 2
    override fun execute(
        args: List<SpellDatum<*>>,
        ctx: CastingContext
    ): Triple<RenderedSpell, Int, List<ParticleSpray>> {
        val entity = args.getChecked<ItemEntity>(0)
        val patterns = args.getChecked<List<SpellDatum<*>>>(1).map {
            if (it.payload is HexPattern)
                it.payload
            else
                throw MishapInvalidIota(it, 0, TranslatableComponent("hexcasting.mishap.invalid_value.list.pattern"))
        }

        val otherHandItem = ctx.caster.getItemInHand(ctx.otherHand)
        if (!otherHandItem.`is`(itemType)) {
            throw MishapBadOffhandItem(otherHandItem, itemType.description)
        }

        ctx.assertEntityInRange(entity)
        if (!ManaHelper.isManaItem(entity.item)) {
            throw MishapBadOffhandItem.of(
                otherHandItem,
                "mana"
            )
        }

        return Triple(Spell(entity, patterns), cost, listOf(ParticleSpray.Burst(entity.position(), 0.5)))
    }

    private data class Spell(val itemEntity: ItemEntity, val patterns: List<HexPattern>) : RenderedSpell {
        override fun cast(ctx: CastingContext) {
            val otherHandItem = ctx.caster.getItemInHand(ctx.otherHand)
            val tag = otherHandItem.orCreateTag
            if (otherHandItem.item is ItemPackagedSpell
                && !tag.contains(ItemPackagedSpell.TAG_MANA)
                && !tag.contains(ItemPackagedSpell.TAG_MAX_MANA)
                && !tag.contains(ItemPackagedSpell.TAG_PATTERNS)
                && itemEntity.isAlive
            ) {
                val manaAmt = ManaHelper.extractAllMana(itemEntity.item)
                if (manaAmt != null) {
                    val tag = otherHandItem.orCreateTag
                    tag.putInt(ItemPackagedSpell.TAG_MANA, manaAmt)
                    tag.putInt(ItemPackagedSpell.TAG_MAX_MANA, manaAmt)

                    val patsTag = ListTag()
                    for (pat in patterns) {
                        patsTag.add(pat.serializeToNBT())
                    }
                    tag.put(ItemPackagedSpell.TAG_PATTERNS, patsTag)

                    if (itemEntity.item.isEmpty)
                        itemEntity.kill()
                }
            }
        }
    }
}