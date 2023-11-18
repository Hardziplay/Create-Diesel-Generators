package com.jesz.createdieselgenerators;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jesz.createdieselgenerators.blocks.BlockRegistry;
import com.jesz.createdieselgenerators.blocks.ct.SpriteShifts;
import com.jesz.createdieselgenerators.blocks.entity.BlockEntityRegistry;
import com.jesz.createdieselgenerators.compat.computercraft.CCProxy;
import com.jesz.createdieselgenerators.config.ConfigRegistry;
import com.jesz.createdieselgenerators.entity.EntityRegistry;
import com.jesz.createdieselgenerators.fluids.FluidRegistry;
import com.jesz.createdieselgenerators.items.ItemRegistry;
import com.jesz.createdieselgenerators.other.CDGPartialModel;
import com.jesz.createdieselgenerators.other.SpoutCanisterFilling;
import com.jesz.createdieselgenerators.ponder.PonderIndex;
import com.jesz.createdieselgenerators.recipes.RecipeRegistry;
import com.jesz.createdieselgenerators.sounds.SoundRegistry;
import com.simibubi.create.AllTags;
import com.simibubi.create.api.behaviour.BlockSpoutingBehaviour;
import com.simibubi.create.compat.Mods;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.simibubi.create.foundation.utility.Components;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.*;

import static com.simibubi.create.AllTags.optionalTag;
import static com.simibubi.create.foundation.utility.Lang.resolveBuilders;

@Mod("createdieselgenerators")
public class CreateDieselGenerators
{
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create("createdieselgenerators");
    public CreateDieselGenerators()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;

        ItemRegistry.register();
        BlockRegistry.register();
        FluidRegistry.register();
        BlockEntityRegistry.register();
        EntityRegistry.register();
        SoundRegistry.register(modEventBus);
        RecipeRegistry.register(modEventBus);
        CreativeTab.register(modEventBus);

        Mods.COMPUTERCRAFT.executeIfInstalled(() -> CCProxy::register);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> onClient(modEventBus, forgeEventBus));
        BlockSpoutingBehaviour.addCustomSpoutInteraction(new ResourceLocation("createdieselgenerators:canister_filling"), new SpoutCanisterFilling());
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ConfigRegistry.SERVER_SPEC, "createdieselgenerators-server.toml");
        MinecraftForge.EVENT_BUS.register(this);
        REGISTRATE.registerEventListeners(modEventBus);
    }
    public static MutableComponent translate(String key, Object... args) {
        return Components.translatable(key, resolveBuilders(args));
    }
    public static Map<String, String> lighterSkins = new HashMap<>();

    public static void onClient(IEventBus modEventBus, IEventBus forgeEventBus) {
        PartialModels.init();
        SpriteShifts.init();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigRegistry.CLIENT_SPEC, "createdieselgenerators-client.toml");
        modEventBus.addListener(CreateDieselGenerators::clientInit);
        modEventBus.addListener(CreateDieselGenerators::onModelRegistry);
        modEventBus.addListener(CDGPartialModel::onModelBake);

    }
    public static void onModelRegistry(ModelEvent.RegisterAdditional event){
        Minecraft.getInstance().getResourceManager().getNamespaces().stream().toList().forEach(n -> {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(n, "lighter_skins.json"));
            if(resource.isEmpty())
                return;
            JsonParser parser = new JsonParser();
            try {
                JsonElement data = parser.parse(resource.get().openAsReader());
                lighterSkins.clear();
                data.getAsJsonArray().forEach(jsonElement -> {
                    lighterSkins.put(jsonElement.getAsJsonObject().getAsJsonPrimitive("name").getAsString(), jsonElement.getAsJsonObject().getAsJsonPrimitive("id").getAsString());
                });
            }catch (IOException ignored) {}
        });
        PartialModels.initSkins();
        CDGPartialModel.onModelRegistry(event);
    }

    public static void clientInit(final FMLClientSetupEvent event) {
        ItemBlockRenderTypes.setRenderLayer(FluidRegistry.ETHANOL.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(FluidRegistry.ETHANOL.getSource(), RenderType.translucent());
        PonderIndex.register();

    }

    public static int getOilAmount(Holder<Biome> biome, int x, int z, long seed){
        Random random = new Random(new Random(seed).nextLong() + (long) x * z);
        int amount = Math.abs(random.nextInt());
        boolean isHighInOil = biome == null || biome.is(AllTags.optionalTag(ForgeRegistries.BIOMES, new ResourceLocation("createdieselgenerators:oil_biomes")));

        if(biome != null && biome.is(AllTags.optionalTag(ForgeRegistries.BIOMES, new ResourceLocation("createdieselgenerators:deny_oil_biomes"))))
            return 0;
        if(isHighInOil ? (random.nextFloat(0, 100) >= ConfigRegistry.HIGH_OIL_PERCENTAGE.get()) : (amount % 100 >= ConfigRegistry.OIL_PERCENTAGE.get()))
            return 0;
        if(isHighInOil)
            return (int) (Mth.clamp(amount % 400000, 8000, 400000)*ConfigRegistry.HIGH_OIL_MULTIPLIER.get());
        return (int) (Mth.clamp(amount % 200, 0, 1000)*ConfigRegistry.OIL_MULTIPLIER.get());
    }

    public static float getGeneratedSpeed(FluidStack stack){
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_strong_slow_burn"))))
            return ConfigRegistry.FAST_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_strong_slow_burn"))))
            return ConfigRegistry.SLOW_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_weak_slow_burn"))))
            return ConfigRegistry.FAST_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_weak_slow_burn"))))
            return ConfigRegistry.SLOW_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_strong_fast_burn"))))
            return ConfigRegistry.FAST_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_strong_fast_burn"))))
            return ConfigRegistry.SLOW_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_weak_fast_burn"))))
            return ConfigRegistry.FAST_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_weak_fast_burn"))))
            return ConfigRegistry.SLOW_SPEED.get().floatValue();

        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:fuel"))) && ConfigRegistry.FUEL_TAG.get())
            return ConfigRegistry.FAST_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:gasoline"))) && ConfigRegistry.GASOLINE_TAG.get())
            return ConfigRegistry.FAST_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:biodiesel"))) && ConfigRegistry.BIODIESEL_TAG.get())
            return ConfigRegistry.FAST_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:diesel"))) && ConfigRegistry.DIESEL_TAG.get())
            return ConfigRegistry.FAST_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:ethanol"))) && ConfigRegistry.ETHANOL_TAG.get())
            return ConfigRegistry.FAST_SPEED.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:plantoil"))) && ConfigRegistry.PLANTOIL_TAG.get())
            return ConfigRegistry.SLOW_SPEED.get().floatValue();
        return 0;
    }
    public static List<FluidStack> getAllFluidTypes( String type ){
        List<FluidStack> fluids = new ArrayList<>(List.of());
        if(type == "fws")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_weak_slow_burn")), 1000).getMatchingFluidStacks());
        if(type == "sws")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_weak_slow_burn")), 1000).getMatchingFluidStacks());
        if(type == "fss")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_strong_slow_burn")), 1000).getMatchingFluidStacks());
        if(type == "sss")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_strong_slow_burn")), 1000).getMatchingFluidStacks());
        if(type == "fwf")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_weak_fast_burn")), 1000).getMatchingFluidStacks());
        if(type == "swf")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_weak_fast_burn")), 1000).getMatchingFluidStacks());
        if(type == "fsf")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_strong_fast_burn")), 1000).getMatchingFluidStacks());
        if(type == "ssf")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_strong_fast_burn")), 1000).getMatchingFluidStacks());
        if(ConfigRegistry.FUEL_TAG.get() && type == "fss")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:fuel")), 1000).getMatchingFluidStacks());
        if(ConfigRegistry.GASOLINE_TAG.get() && type == "fss")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:gasoline")), 1000).getMatchingFluidStacks());
        if(ConfigRegistry.BIODIESEL_TAG.get() && type == "fss")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:biodiesel")), 1000).getMatchingFluidStacks());
        if(ConfigRegistry.DIESEL_TAG.get() && type == "fss")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:diesel")), 1000).getMatchingFluidStacks());
        if(ConfigRegistry.ETHANOL_TAG.get() && type == "fws")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:ethanol")), 1000).getMatchingFluidStacks());
        if(ConfigRegistry.PLANTOIL_TAG.get() && type == "sss")
            fluids.addAll(FluidIngredient.fromTag(optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:plantoil")), 1000).getMatchingFluidStacks());

        return fluids;
    }
    public static float getGeneratedStress(FluidStack stack) {
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_strong_slow_burn"))))
            return ConfigRegistry.STRONG_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_strong_slow_burn"))))
            return ConfigRegistry.STRONG_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_weak_slow_burn"))))
            return ConfigRegistry.WEAK_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_weak_slow_burn"))))
            return ConfigRegistry.WEAK_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_strong_fast_burn"))))
            return ConfigRegistry.STRONG_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_strong_fast_burn"))))
            return ConfigRegistry.STRONG_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_weak_fast_burn"))))
            return ConfigRegistry.WEAK_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_weak_fast_burn"))))
            return ConfigRegistry.WEAK_STRESS.get().floatValue();

        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:fuel"))))
            return ConfigRegistry.STRONG_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:gasoline"))))
            return ConfigRegistry.STRONG_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:biodiesel"))))
            return ConfigRegistry.STRONG_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:diesel"))))
            return ConfigRegistry.STRONG_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:ethanol"))))
            return ConfigRegistry.WEAK_STRESS.get().floatValue();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("forge:plantoil"))))
            return ConfigRegistry.STRONG_STRESS.get().floatValue();
        return 0;
    }

    public static int getBurnRate(FluidStack stack) {

        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_strong_fast_burn"))))
            return ConfigRegistry.FAST_BURN_RATE.get();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_strong_fast_burn"))))
            return ConfigRegistry.FAST_BURN_RATE.get();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_fast_weak_fast_burn"))))
            return ConfigRegistry.FAST_BURN_RATE.get();
        if(stack.getFluid().is(AllTags.optionalTag(ForgeRegistries.FLUIDS, new ResourceLocation("createdieselgenerators:diesel_engine_fuel_slow_weak_fast_burn"))))
            return ConfigRegistry.FAST_BURN_RATE.get();

        return ConfigRegistry.SLOW_BURN_RATE.get();
    }
}
