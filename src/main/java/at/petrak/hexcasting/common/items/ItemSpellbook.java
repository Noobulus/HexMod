package at.petrak.hexcasting.common.items;

import at.petrak.hexcasting.api.spell.SpellDatum;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public class ItemSpellbook extends ItemDataHolder {
    public static String TAG_SELECTED_PAGE = "page_idx";
    // this is a CompoundTag of string numerical keys to SpellData
    // it is 1-indexed, so that 0/0 can be the special case of "it is empty"
    public static String TAG_PAGES = "pages";

    // this stores the names of pages, to be restored when you scroll
    // it is 1-indexed, and the 0-case for TAG_PAGES will be treated as 1
    public static String TAG_PAGE_NAMES = "page_names";

    public ItemSpellbook(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
        TooltipFlag isAdvanced) {
        var tag = stack.getOrCreateTag();
        if (tag.contains(TAG_SELECTED_PAGE)) {
            var pageIdx = tag.getInt(TAG_SELECTED_PAGE);
            var pages = tag.getCompound(ItemSpellbook.TAG_PAGES);
            int highest = HighestPage(pages);
            if (highest != 0) {
                tooltip.add(new TranslatableComponent("hexcasting.tooltip.spellbook.page",
                        new TextComponent(String.valueOf(pageIdx)).withStyle(ChatFormatting.WHITE),
                        new TextComponent(String.valueOf(highest)).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(new TranslatableComponent("hexcasting.tooltip.spellbook.empty").withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(new TranslatableComponent("hexcasting.tooltip.spellbook.empty").withStyle(ChatFormatting.GRAY));
        }

        super.appendHoverText(stack, level, tooltip, isAdvanced);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        var tag = stack.getOrCreateTag();
        int index;
        if (ArePagesEmpty(tag)) {
            index = 0;
        } else if (!tag.contains(TAG_SELECTED_PAGE)) {
            index = 1;
        } else {
            index = tag.getInt(TAG_SELECTED_PAGE);
            if (index == 0) index = 1;
        }
        tag.putInt(TAG_SELECTED_PAGE, index);

        int shiftedIdx = Math.max(1, index);
        String nameKey = String.valueOf(shiftedIdx);
        CompoundTag names = tag.getCompound(TAG_PAGE_NAMES);
        if (stack.hasCustomHoverName()) {
            names.putString(nameKey, Component.Serializer.toJson(stack.getHoverName()));
        } else {
            names.remove(nameKey);
        }
        tag.put(TAG_PAGE_NAMES, names);
    }

    public static boolean ArePagesEmpty(CompoundTag tag) {
        return !tag.contains(ItemSpellbook.TAG_PAGES) ||
            tag.getCompound(ItemSpellbook.TAG_PAGES).isEmpty();
    }

    @Override
    public @Nullable CompoundTag readDatumTag(ItemStack stack) {
        if (!stack.hasTag()) {
            return null;
        }
        var tag = stack.getTag();

        int idx;
        if (tag.contains(TAG_SELECTED_PAGE)) {
            idx = tag.getInt(TAG_SELECTED_PAGE);
        } else {
            idx = 0;
        }
        var key = String.valueOf(idx);
        if (tag.contains(TAG_PAGES)) {
            var pagesTag = tag.getCompound(TAG_PAGES);
            if (pagesTag.contains(key)) {
                return pagesTag.getCompound(key);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public boolean canWrite(CompoundTag tag, SpellDatum<?> datum) {
        return true;
    }

    public void writeDatum(CompoundTag tag, SpellDatum<?> datum) {
        int idx;
        if (tag.contains(TAG_SELECTED_PAGE)) {
            idx = tag.getInt(TAG_SELECTED_PAGE);
            // But we want to write to page *1* to start if this is our first page
            if (idx == 0 && ArePagesEmpty(tag)) {
                idx = 1;
            }
        } else {
            idx = 1;
        }
        var key = String.valueOf(idx);
        if (tag.contains(TAG_PAGES)) {
            tag.getCompound(TAG_PAGES).put(key, datum.serializeToNBT());
        } else {
            var pagesTag = new CompoundTag();
            pagesTag.put(key, datum.serializeToNBT());
            tag.put(TAG_PAGES, pagesTag);
        }
    }

    public static int HighestPage(CompoundTag tag) {
        var highestKey = tag.getAllKeys().stream().flatMap(s -> {
            try {
                return Stream.of(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                return Stream.empty();
            }
        }).max(Integer::compare);
        return highestKey.orElse(0);
    }

    public static void RotatePageIdx(ItemStack stack, CompoundTag tag, boolean increase) {
        int newIdx;
        if (ArePagesEmpty(tag)) {
            newIdx = 0;
        } else if (tag.contains(TAG_SELECTED_PAGE)) {
            var delta = increase ? 1 : -1;
            newIdx = Math.max(1, tag.getInt(TAG_SELECTED_PAGE) + delta);
        } else {
            newIdx = 1;
        }
        tag.putInt(TAG_SELECTED_PAGE, newIdx);

        CompoundTag names = tag.getCompound(TAG_PAGE_NAMES);
        int shiftedIdx = Math.max(1, newIdx);
        String nameKey = String.valueOf(shiftedIdx);
        if (names.contains(nameKey, Tag.TAG_STRING)) {
            stack.setHoverName(Component.Serializer.fromJson(names.getString(nameKey)));
        } else {
            stack.resetHoverName();
        }
    }
}
