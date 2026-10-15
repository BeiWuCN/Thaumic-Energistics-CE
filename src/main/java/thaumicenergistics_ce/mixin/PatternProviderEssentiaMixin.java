package thaumicenergistics_ce.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.EssentiaPatternProvider;
import thaumicenergistics_ce.integration.ae2.EssentiaProviderTarget;
import thaumicenergistics_ce.init.ModItems;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderEssentiaMixin implements EssentiaPatternProvider {
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private IActionSource actionSource;
    @Shadow @Final private appeng.helpers.patternprovider.PatternProviderReturnInventory returnInv;
    @Unique private AppEngInternalInventory tce$card;
    @Unique private thaumicenergistics_ce.integration.ae2.EssentiaProviderReturn tce$return;

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("TAIL"))
    private void tce$initialize(appeng.api.networking.IManagedGridNode node, PatternProviderLogicHost host,
            int slots, CallbackInfo ci) {
        var returnGate = (thaumicenergistics_ce.integration.ae2.EssentiaReturnGate)
                returnInv;
        returnGate.setEssentiaGate(this::essentiaEnabled);
        returnGate.setEssentiaChangeListener(() ->
                ((thaumicenergistics_ce.integration.ae2.EssentiaProviderReturn)
                        essentiaReturnStorage()).inventoryChanged());
    }

    @Override
    public appeng.helpers.externalstorage.GenericStackInv essentiaBuffer() {
        return ((PatternProviderLogic) (Object) this).getReturnInv();
    }

    @Override
    public com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage essentiaReturnStorage() {
        if (tce$return == null) tce$return = new thaumicenergistics_ce.integration.ae2.EssentiaProviderReturn(
                this);
        return tce$return;
    }

    @Override
    public InternalInventory essentiaCardInventory() {
        if (tce$card == null) {
            tce$card = new AppEngInternalInventory((PatternProviderLogic) (Object) this, 1, 1);
            tce$card.setFilter(new IAEItemFilter() {
                @Override
                public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
                    return essentiaSupportEnabled() && stack.is(ModItems.ESSENTIA_ACCESS_CARD.get());
                }
            });
        }
        return tce$card;
    }

    @Override
    public boolean essentiaSupportEnabled() {
        var icon = host.getTerminalIcon();
        if (icon == null) return false;
        var namespace = host instanceof appeng.parts.AEBasePart
                ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(icon.getItem()).getNamespace()
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                        host.getBlockEntity().getBlockState().getBlock()).getNamespace();
        return switch (namespace) {
            case "ae2" -> thaumicenergistics_ce.ThEConfig.PATTERN_PROVIDER_AE2.get();
            case "extendedae" -> thaumicenergistics_ce.ThEConfig.PATTERN_PROVIDER_EXTENDED_AE.get();
            case "extendedae_plus" -> thaumicenergistics_ce.ThEConfig.PATTERN_PROVIDER_EXTENDED_AE_PLUS.get();
            case "advanced_ae" -> thaumicenergistics_ce.ThEConfig.PATTERN_PROVIDER_ADVANCED_AE.get();
            case "ae2lt" -> thaumicenergistics_ce.ThEConfig.PATTERN_PROVIDER_LIGHTNING.get();
            default -> false;
        };
    }

    @Override
    public boolean essentiaEnabled() {
        if (!essentiaSupportEnabled()) return false;
        var be = host.getBlockEntity();
        if (be == null || be.isRemoved()) return false;
        if (host instanceof appeng.parts.AEBasePart part
                && (part.getHost() == null || part.getHost().getPart(part.getSide()) != part)) return false;
        return essentiaCardInventory().getStackInSlot(0).is(ModItems.ESSENTIA_ACCESS_CARD.get());
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void tce$save(CompoundTag output, HolderLookup.Provider registries, CallbackInfo ci) {
        ((AppEngInternalInventory) essentiaCardInventory()).writeToNBT(output, "tceEssentiaCard", registries);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void tce$load(CompoundTag input, HolderLookup.Provider registries, CallbackInfo ci) {
        ((AppEngInternalInventory) essentiaCardInventory()).readFromNBT(input, "tceEssentiaCard", registries);
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void tce$drop(List<ItemStack> drops, CallbackInfo ci) {
        var card = essentiaCardInventory().getStackInSlot(0);
        if (!card.isEmpty()) drops.add(card.copy());
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void tce$clear(CallbackInfo ci) { essentiaCardInventory().clear(); }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void tce$gate(IPatternDetails pattern, KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        if (!essentiaEnabled()) {
            for (var input : inputs) for (var entry : input) {
                if (entry.getKey() instanceof AEssentiaKey) { cir.setReturnValue(false); return; }
            }
        }
    }

    @Inject(method = "findAdapter", at = @At("RETURN"), cancellable = true)
    private void tce$target(Direction side, CallbackInfoReturnable<PatternProviderTarget> cir) {
        var be = host.getBlockEntity();
        cir.setReturnValue(new EssentiaProviderTarget(cir.getReturnValue(), be.getLevel(),
                be.getBlockPos().relative(side), side.getOpposite(), actionSource, this::essentiaEnabled));
    }
}
