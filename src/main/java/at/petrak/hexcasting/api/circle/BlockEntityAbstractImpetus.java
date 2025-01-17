package at.petrak.hexcasting.api.circle;

import at.petrak.hexcasting.HexConfig;
import at.petrak.hexcasting.api.spell.ParticleSpray;
import at.petrak.hexcasting.common.casting.CastingContext;
import at.petrak.hexcasting.common.casting.CastingHarness;
import at.petrak.hexcasting.common.casting.SpellCircleContext;
import at.petrak.hexcasting.common.casting.colors.FrozenColorizer;
import at.petrak.hexcasting.common.items.HexItems;
import at.petrak.hexcasting.common.lib.HexCapabilities;
import at.petrak.hexcasting.common.lib.HexSounds;
import at.petrak.paucal.api.PaucalBlockEntity;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class BlockEntityAbstractImpetus extends PaucalBlockEntity implements ICapabilityProvider {
    public static final String
        TAG_ACTIVATOR = "activator",
        TAG_COLORIZER = "colorizer",
        TAG_NEXT_BLOCK = "next_block",
        TAG_TRACKED_BLOCKS = "tracked_blocks",
        TAG_FOUND_ALL = "found_all",
        TAG_MANA = "mana",
        TAG_LAST_MISHAP = "last_mishap";

    @Nullable
    private UUID activator = null;
    @Nullable
    private FrozenColorizer colorizer = null;
    @Nullable
    private BlockPos nextBlock = null;
    @Nullable
    private List<BlockPos> trackedBlocks = null;
    private transient Set<BlockPos> knownBlocks = null;
    private boolean foundAll = false;
    @Nullable
    private Component lastMishap = null;

    private int mana = 0;
    private final LazyOptional<IItemHandler> inventoryHandlerLazy;

    public BlockEntityAbstractImpetus(BlockEntityType<?> pType, BlockPos pWorldPosition, BlockState pBlockState) {
        super(pType, pWorldPosition, pBlockState);
        inventoryHandlerLazy = LazyOptional.of(() -> ITEM_HANDLER);
    }

    abstract public boolean activatorAlwaysInRange();

    public int getMana() {
        return this.mana;
    }

    public void setMana(int mana) {
        this.mana = mana;
    }

    @Nullable
    public Component getLastMishap() {
        return lastMishap;
    }

    public void setLastMishap(@Nullable Component lastMishap) {
        this.lastMishap = lastMishap;
    }

    public void activateSpellCircle(ServerPlayer activator) {
        if (this.nextBlock != null) {
            return;
        }
        this.level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), this.getTickSpeed());

        this.activator = activator.getUUID();
        this.nextBlock = this.getBlockPos();
        this.trackedBlocks = new ArrayList<>();
        this.knownBlocks = new HashSet<>();
        var maybeCap = activator.getCapability(HexCapabilities.PREFERRED_COLORIZER).resolve();
        maybeCap.ifPresent(capPreferredColorizer -> this.colorizer = capPreferredColorizer.colorizer);

        this.level.setBlockAndUpdate(this.getBlockPos(),
            this.getBlockState().setValue(BlockAbstractImpetus.ENERGIZED, true));
        this.stepCircle();
    }

    public List<Pair<ItemStack, Component>> getScryingLensOverlay(BlockState state, BlockPos pos,
        LocalPlayer observer, ClientLevel world, InteractionHand lensHand) {
        var out = new ArrayList<Pair<ItemStack, Component>>();
        if (world.getBlockEntity(pos) instanceof BlockEntityAbstractImpetus beai) {
            var dustCount = (float) beai.getMana() / (float) HexConfig.dustManaAmount.get();
            var dustCmp = new TranslatableComponent("hexcasting.tooltip.lens.impetus.mana",
                String.format("%.2f", dustCount));
            out.add(new Pair<>(new ItemStack(HexItems.AMETHYST_DUST.get()), dustCmp));

            if (this.lastMishap != null) {
                out.add(new Pair<>(new ItemStack(HexItems.SCRYING_LENS.get()), this.lastMishap));
            }
        }
        return out;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return inventoryHandlerLazy.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryHandlerLazy.invalidate();
    }

    @Override
    protected void saveModData(CompoundTag tag) {
        if (this.activator != null && this.colorizer != null && this.nextBlock != null && this.trackedBlocks != null) {
            tag.putUUID(TAG_ACTIVATOR, this.activator);
            tag.put(TAG_NEXT_BLOCK, NbtUtils.writeBlockPos(this.nextBlock));
            tag.put(TAG_COLORIZER, this.colorizer.serialize());
            tag.putBoolean(TAG_FOUND_ALL, this.foundAll);

            var trackeds = new ListTag();
            for (var tracked : this.trackedBlocks) {
                trackeds.add(NbtUtils.writeBlockPos(tracked));
            }
            tag.put(TAG_TRACKED_BLOCKS, trackeds);
        }

        tag.putInt(TAG_MANA, this.mana);
        if (this.lastMishap != null) {
            tag.putString(TAG_LAST_MISHAP, Component.Serializer.toJson(this.lastMishap));
        }
    }

    @Override
    protected void loadModData(CompoundTag tag) {
        if (tag.contains(TAG_ACTIVATOR) && tag.contains(TAG_COLORIZER) && tag.contains(TAG_NEXT_BLOCK)
            && tag.contains(TAG_TRACKED_BLOCKS)) {
            this.activator = tag.getUUID(TAG_ACTIVATOR);
            this.colorizer = FrozenColorizer.deserialize(tag.getCompound(TAG_COLORIZER));
            this.nextBlock = NbtUtils.readBlockPos(tag.getCompound(TAG_NEXT_BLOCK));
            this.foundAll = tag.getBoolean(TAG_FOUND_ALL);
            var trackeds = tag.getList(TAG_TRACKED_BLOCKS, Tag.TAG_COMPOUND);
            this.trackedBlocks = new ArrayList<>(trackeds.size());
            this.knownBlocks = new HashSet<>();
            for (var tracked : trackeds) {
                var pos = NbtUtils.readBlockPos((CompoundTag) tracked);
                this.trackedBlocks.add(pos);
                this.knownBlocks.add(pos);
            }
        }

        this.mana = tag.getInt(TAG_MANA);
        if (tag.contains(TAG_LAST_MISHAP)) {
            this.lastMishap = Component.Serializer.fromJson(tag.getString(TAG_LAST_MISHAP));
        }
    }

    void stepCircle() {
        this.setChanged();

        // haha which silly idiot would have done something like this
        if (this.activator == null || this.colorizer == null || this.nextBlock == null || this.trackedBlocks == null) {
            return;
        }

        var possibleErrorPos = this.checkEverythingOk();
        if (possibleErrorPos != null) {
            this.sfx(possibleErrorPos, false);
            this.stopCasting();
            return;
        }

        if (this.foundAll) {
            this.castSpell();
            this.stopCasting();
            return;
        }

        // This should only fail if we remove blocks halfway through casting
        var bsHere = this.level.getBlockState(this.nextBlock);
        if (!this.trackedBlocks.isEmpty() && bsHere.getBlock() instanceof BlockAbstractImpetus) {
            // no two impetuses!
            this.sfx(this.nextBlock, false);
            this.stopCasting();
            return;
        }
        var blockHere = bsHere.getBlock();
        if (!(blockHere instanceof BlockCircleComponent cc)) {
            this.sfx(this.nextBlock, false);
            this.stopCasting();
            return;
        }
        // Awesome we know this block is OK
        var thisNormal = cc.normalDir(this.nextBlock, bsHere, this.level);
        var possibleExits = cc.exitDirections(this.nextBlock, bsHere, this.level);
        BlockPos foundPos = null;
        for (var exit : possibleExits) {
            var neighborPos = this.nextBlock.relative(exit);
            var blockThere = this.level.getBlockState(neighborPos);
            // at this point, we haven't actually added nextBlock to trackedBlocks
            // so, in the smallest circle case (a 2x2), this will have a size of 3 (with this block being the 4th).
            var closedLoop = (this.trackedBlocks.size() >= 3 && this.trackedBlocks.get(0).equals(neighborPos));
            var mightBeOkThere = closedLoop
                || this.trackedBlocks.isEmpty()
                || !this.trackedBlocks.get(this.trackedBlocks.size() - 1).equals(neighborPos);
            if (mightBeOkThere
                && blockThere.getBlock() instanceof BlockCircleComponent cc2
                && cc2.canEnterFromDirection(exit.getOpposite(), thisNormal, neighborPos, blockThere, this.level)
                // another good use for the implies operator 😩
                && (!blockThere.getValue(BlockCircleComponent.ENERGIZED) || this.knownBlocks.contains(neighborPos))) {
                if (foundPos == null) {
                    foundPos = neighborPos;
                    this.foundAll |= closedLoop;
                } else {
                    // uh oh, fork in the road
                    this.sfx(this.nextBlock, false);
                    this.stopCasting();
                    return;
                }
            }
        }
        if (foundPos != null) {
            // pog
            this.trackedBlocks.add(this.nextBlock);
            this.knownBlocks.add(this.nextBlock);
            this.nextBlock = foundPos;
        } else {
            // end of the line
            this.sfx(this.nextBlock, false);
            this.stopCasting();
            return;
        }

        var lastPos = this.trackedBlocks.get(this.trackedBlocks.size() - 1);
        var justTrackedBlock = this.level.getBlockState(lastPos);
        this.level.setBlockAndUpdate(lastPos, justTrackedBlock.setValue(BlockCircleComponent.ENERGIZED, true));
        this.sfx(lastPos, true);

        this.level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), this.getTickSpeed());
    }

    private void castSpell() {
        var player = this.getPlayer();

        if (player instanceof ServerPlayer splayer) {
            var bounds = getBounds(this.trackedBlocks);

            var ctx = new CastingContext(splayer, InteractionHand.MAIN_HAND,
                new SpellCircleContext(this.getBlockPos(), bounds, this.activatorAlwaysInRange()));
            var harness = new CastingHarness(ctx);

            var castSpell = false;
            BlockPos erroredPos = null;
            for (var tracked : this.trackedBlocks) {
                var bs = this.level.getBlockState(tracked);
                if (bs.getBlock() instanceof BlockCircleComponent cc) {
                    var newPattern = cc.getPattern(tracked, bs, this.level);
                    if (newPattern != null) {
                        var info = harness.executeNewPattern(newPattern, splayer.getLevel());
                        if (info.getWasSpellCast()) {
                            castSpell = true;
                        }
                        if (info.getWasPrevPatternInvalid()) {
                            erroredPos = tracked;
                            break;
                        }
                    }
                }
            }

            if (castSpell) {
                this.level.playSound(null, this.getBlockPos(), HexSounds.SPELL_CIRCLE_CAST.get(), SoundSource.BLOCKS,
                    2f, 1f);
            }
            if (erroredPos != null) {
                this.sfx(erroredPos, false);
            }
        }
    }

    @Contract(pure = true)
    private static AABB getBounds(List<BlockPos> poses) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (var pos : poses) {
            if (pos.getX() < minX) {
                minX = pos.getX();
            }
            if (pos.getY() < minY) {
                minY = pos.getY();
            }
            if (pos.getZ() < minZ) {
                minZ = pos.getZ();
            }
            if (pos.getX() > maxX) {
                maxX = pos.getX();
            }
            if (pos.getY() > maxY) {
                maxY = pos.getY();
            }
            if (pos.getZ() > maxZ) {
                maxZ = pos.getZ();
            }
        }

        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    @Nullable
    private BlockPos checkEverythingOk() {
        // if they logged out or changed dimensions or something
        if (this.getPlayer() == null) {
            return this.getBlockPos();
        }

        for (var pos : this.trackedBlocks) {
            if (!(this.level.getBlockState(pos).getBlock() instanceof BlockCircleComponent)) {
                return pos;
            }
        }

        if (this.trackedBlocks.size() > HexConfig.maxSpellCircleLength.get()) {
            return this.trackedBlocks.get(this.trackedBlocks.size() - 1);
        }

        return null;
    }

    private void sfx(BlockPos pos, boolean success) {
        Vec3 vpos;
        Vec3 vecOutDir;

        var bs = this.level.getBlockState(pos);
        if (bs.getBlock() instanceof BlockCircleComponent bcc) {
            var outDir = bcc.normalDir(pos, bs, this.level);
            var height = bcc.particleHeight(pos, bs, this.level);
            vecOutDir = new Vec3(outDir.step());
            vpos = Vec3.atCenterOf(pos).add(vecOutDir.scale(height));
        } else {
            // we probably are doing this because it's an error and we removed a block
            vpos = Vec3.atCenterOf(pos);
            vecOutDir = new Vec3(0, 0, 0);
        }

        if (this.level instanceof ServerLevel serverLevel) {
            var spray = new ParticleSpray(vpos, vecOutDir.scale(success ? 1.0 : 1.5), success ? 0.1 : 0.5,
                Mth.PI / (success ? 4 : 2), success ? 30 : 100);
            spray.sprayParticles(serverLevel,
                success ? this.colorizer : new FrozenColorizer(HexItems.DYE_COLORIZERS.get(DyeColor.RED).get(),
                    this.activator));
        }

        var pitch = 1f;
        var sound = HexSounds.SPELL_CIRCLE_FAIL.get();
        if (success) {
            sound = HexSounds.SPELL_CIRCLE_FIND_BLOCK.get();
            // This is a good use of my time
            var note = this.trackedBlocks.size() - 1;
            var semitone = this.semitoneFromScale(note);
            pitch = (float) Math.pow(2.0, (semitone - 8) / 12d);
        }
        level.playSound(null, vpos.x, vpos.y, vpos.z, sound, SoundSource.BLOCKS, 1f, pitch);
    }

    protected void stopCasting() {
        if (this.trackedBlocks != null) {
            for (var tracked : this.trackedBlocks) {
                var bs = this.level.getBlockState(tracked);
                if (bs.getBlock() instanceof BlockCircleComponent) {
                    this.level.setBlockAndUpdate(tracked, bs.setValue(BlockCircleComponent.ENERGIZED, false));
                }
            }
        }

        this.activator = null;
        this.nextBlock = null;
        this.trackedBlocks = null;
        this.foundAll = false;

        // without this check, breaking the block will just immediately replace it with
        // the new unenergized state
        if (this.level.getBlockState(this.getBlockPos()).getBlock() instanceof BlockAbstractImpetus) {
            this.level.setBlockAndUpdate(this.getBlockPos(),
                this.getBlockState().setValue(BlockCircleComponent.ENERGIZED, false));
        }
    }

    @Nullable
    protected Player getPlayer() {
        return this.level.getPlayerByUUID(this.activator);
    }

    protected int getTickSpeed() {
        if (this.trackedBlocks == null) {
            return 10;
        } else {
            return Math.max(2, 10 - trackedBlocks.size() / 3);
        }
    }

    protected int semitoneFromScale(int note) {
        var blockBelow = this.level.getBlockState(this.getBlockPos().below());
        var scale = MAJOR_SCALE;
        if (blockBelow.is(Blocks.CRYING_OBSIDIAN)) {
            scale = MINOR_SCALE;
        } else if (blockBelow.is(BlockTags.DOORS) || blockBelow.is(BlockTags.TRAPDOORS)) {
            scale = DORIAN_SCALE;
        } else if (blockBelow.is(Blocks.PISTON) || blockBelow.is(Blocks.STICKY_PISTON)) {
            scale = MIXOLYDIAN_SCALE;
        } else if (blockBelow.is(Blocks.BLUE_WOOL)
            || blockBelow.is(Blocks.BLUE_CONCRETE) || blockBelow.is(Blocks.BLUE_CONCRETE_POWDER)
            || blockBelow.is(Blocks.BLUE_TERRACOTTA) || blockBelow.is(Blocks.BLUE_GLAZED_TERRACOTTA)
            || blockBelow.is(Blocks.BLUE_STAINED_GLASS) || blockBelow.is(Blocks.BLUE_STAINED_GLASS_PANE)) {
            scale = BLUES_SCALE;
        } else if (blockBelow.is(Blocks.BONE_BLOCK)) {
            scale = BAD_TIME;
        } else if (blockBelow.is(Blocks.COMPOSTER)) {
            scale = SUSSY_BAKA;
        }

        note = Mth.clamp(note, 0, scale.length - 1);
        return scale[note];
    }

    // this is a good use of my time
    private static final int[] MAJOR_SCALE = {0, 2, 4, 5, 7, 9, 11, 12};
    private static final int[] MINOR_SCALE = {0, 2, 3, 5, 7, 8, 11, 12};
    private static final int[] DORIAN_SCALE = {0, 2, 3, 5, 7, 9, 10, 12};
    private static final int[] MIXOLYDIAN_SCALE = {0, 2, 4, 5, 7, 9, 10, 12};
    private static final int[] BLUES_SCALE = {0, 3, 5, 6, 7, 10, 12};
    private static final int[] BAD_TIME = {0, 0, 12, 7, 6, 5, 3, 0, 3, 5};
    private static final int[] SUSSY_BAKA = {5, 8, 10, 11, 10, 8, 5, 3, 7, 5};

    protected IItemHandler ITEM_HANDLER = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            var manamount = getManaAmount(stack);
            if (manamount != null) {
                if (!simulate) {
                    BlockEntityAbstractImpetus.this.mana += manamount;
                    BlockEntityAbstractImpetus.this.setChanged();
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
                }
                return ItemStack.EMPTY.copy();
            }
            return stack.copy();
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY.copy();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return getManaAmount(stack) != null;
        }

        // a separate method from the ctx or harness or whatever cause it's different and special
        private static @Nullable Integer getManaAmount(ItemStack stack) {
            int baseAmt;
            if (stack.is(HexItems.AMETHYST_DUST.get())) {
                baseAmt = HexConfig.dustManaAmount.get();
            } else if (stack.is(Items.AMETHYST_SHARD)) {
                baseAmt = HexConfig.shardManaAmount.get();
            } else if (stack.is(HexItems.CHARGED_AMETHYST.get())) {
                baseAmt = HexConfig.chargedCrystalManaAmount.get();
            } else {
                return null;
            }

            return baseAmt * stack.getCount();
        }
    };
}
