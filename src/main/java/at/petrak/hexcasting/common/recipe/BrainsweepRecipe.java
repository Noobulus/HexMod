package at.petrak.hexcasting.common.recipe;

import at.petrak.hexcasting.common.recipe.ingredient.StateIngredient;
import at.petrak.hexcasting.common.recipe.ingredient.StateIngredientHelper;
import at.petrak.hexcasting.common.recipe.ingredient.VillagerIngredient;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;

// God I am a horrible person
public record BrainsweepRecipe(
    ResourceLocation id,
    StateIngredient blockIn,
    VillagerIngredient villagerIn,
    BlockState result
) implements Recipe<Container> {


    public boolean matches(BlockState blockIn, Villager villagerIn) {
        return this.blockIn.test(blockIn) && this.villagerIn.test(villagerIn);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeType<?> getType() {
        return HexRecipeSerializers.BRAINSWEEP_TYPE;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HexRecipeSerializers.BRAINSWEEP.get();
    }

    // in order to get this to be a "Recipe" we need to do a lot of bending-over-backwards
    // to get the implementation to be satisfied even though we never use it
    @Override
    public boolean matches(Container pContainer, Level pLevel) {
        return false;
    }

    @Override
    public ItemStack assemble(Container pContainer) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return false;
    }

    @Override
    public ItemStack getResultItem() {
        return ItemStack.EMPTY.copy();
    }

    public static class Serializer extends ForgeRegistryEntry<RecipeSerializer<?>> implements RecipeSerializer<BrainsweepRecipe> {
        @Override
        public BrainsweepRecipe fromJson(ResourceLocation recipeID, JsonObject json) {
            var blockIn = StateIngredientHelper.deserialize(GsonHelper.getAsJsonObject(json, "blockIn"));
            var villagerIn = VillagerIngredient.deserialize(GsonHelper.getAsJsonObject(json, "villagerIn"));
            var result = StateIngredientHelper.readBlockState(GsonHelper.getAsJsonObject(json, "result"));
            return new BrainsweepRecipe(recipeID, blockIn, villagerIn, result);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, BrainsweepRecipe recipe) {
            recipe.blockIn.write(buf);
            recipe.villagerIn.write(buf);
            buf.writeVarInt(Block.getId(recipe.result));
        }

        @Nullable
        @Override
        public BrainsweepRecipe fromNetwork(ResourceLocation recipeID, FriendlyByteBuf buf) {
            var blockIn = StateIngredientHelper.read(buf);
            var villagerIn = VillagerIngredient.read(buf);
            var result = Block.stateById(buf.readVarInt());
            return new BrainsweepRecipe(recipeID, blockIn, villagerIn, result);
        }
    }
}
