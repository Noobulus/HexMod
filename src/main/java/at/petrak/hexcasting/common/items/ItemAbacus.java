package at.petrak.hexcasting.common.items;

import at.petrak.hexcasting.api.spell.SpellDatum;
import at.petrak.hexcasting.common.lib.HexSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ItemAbacus extends ItemDataHolder {
    public static final String TAG_VALUE = "value";

    public ItemAbacus(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public @Nullable CompoundTag readDatumTag(ItemStack stack) {
        double num;
        var tag = stack.getTag();
        if (tag == null) {
            num = 0d;
        } else {
            num = tag.getDouble(TAG_VALUE);
        }
        var datum = SpellDatum.make(num);
        return datum.serializeToNBT();
    }

    @Override
    public boolean canWrite(CompoundTag tag, SpellDatum<?> datum) {
        return false;
    }

    @Override
    public void writeDatum(CompoundTag tag, SpellDatum<?> datum) {
        // nope
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {

            var tag = stack.getOrCreateTag();
            double oldNum = 0d;
            if (tag.contains(TAG_VALUE, Tag.TAG_DOUBLE)) {
                oldNum = tag.getDouble(TAG_VALUE);
            }
            tag.putDouble(TAG_VALUE, 0d);

            player.playSound(HexSounds.ABACUS_SHAKE.get(), 1f, 1f);

            var key = "hexcasting.tooltip.abacus.reset";
            if (oldNum == 69) {
                key += ".nice";
            }
            player.displayClientMessage(new TranslatableComponent(key), true);

            return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
        } else {
            return InteractionResultHolder.pass(stack);
        }
    }
}
