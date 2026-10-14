package thaumicenergistics_ce.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.config.ThEConfig;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.patternprovider.*;
import thaumicenergistics_ce.registry.TECEItems;

@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.blockentity.PigmeePatternProviderBlockEntity", remap = false)
public abstract class PigmeeProviderEssentiaMixin extends AENetworkedBlockEntity
        implements EssentiaPatternProvider, InternalInventoryHost {
    @Unique private AppEngInternalInventory tce$card;
    @Unique private GenericStackInv tce$buffer;
    @Unique private EssentiaProviderReturn tce$return;

    protected PigmeeProviderEssentiaMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tce$initialize(CallbackInfo ci) {
        try {
            tce$buffer = (GenericStackInv) getClass().getMethod("getReturnInventory").invoke(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Pigmee return inventory is unavailable", e);
        }
        var gate = (EssentiaReturnGate) tce$buffer;
        gate.setEssentiaGate(this::essentiaEnabled);
        gate.setEssentiaChangeListener(() -> ((EssentiaProviderReturn) essentiaReturnStorage()).inventoryChanged());
    }
    @Override public GenericStackInv essentiaBuffer() { return tce$buffer; }
    @Override public boolean essentiaSupportEnabled() { return ThEConfig.PATTERN_PROVIDER_LIGHTNING.get(); }
    @Override public boolean essentiaEnabled() {
        return !isRemoved() && essentiaSupportEnabled()
                && essentiaCardInventory().getStackInSlot(0).is(TECEItems.ESSENTIA_ACCESS_CARD.get());
    }
    @Override public InternalInventory essentiaCardInventory() {
        if (tce$card == null) {
            tce$card = new AppEngInternalInventory(this, 1, 1);
            tce$card.setFilter(new IAEItemFilter() {
                @Override public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                    return essentiaSupportEnabled() && stack.is(TECEItems.ESSENTIA_ACCESS_CARD.get());
                }
            });
        }
        return tce$card;
    }
    @Override public IEssentiaStorage essentiaReturnStorage() {
        if (tce$return == null) tce$return = new EssentiaProviderReturn(this);
        return tce$return;
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void tce$save(ValueOutput output, CallbackInfo ci) {
        ((AppEngInternalInventory) essentiaCardInventory()).writeToNBT(output, "tceEssentiaCard");
    }
    @Inject(method = "loadTag", at = @At("TAIL"))
    private void tce$load(ValueInput input, CallbackInfo ci) {
        ((AppEngInternalInventory) essentiaCardInventory()).readFromNBT(input, "tceEssentiaCard");
    }
    @Inject(method = "addAdditionalDrops", at = @At("TAIL"))
    private void tce$drop(Level level, BlockPos pos, List<ItemStack> drops, CallbackInfo ci) {
        var card = essentiaCardInventory().getStackInSlot(0);
        if (!card.isEmpty()) drops.add(card.copy());
    }
    @Inject(method = "clearContent", at = @At("TAIL"))
    private void tce$clear(CallbackInfo ci) { essentiaCardInventory().clear(); }
    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void tce$gate(IPatternDetails details, KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        if (essentiaEnabled()) return;
        for (var input : inputs) for (var entry : input) {
            if (entry.getKey() instanceof AEssentiaKey) { cir.setReturnValue(false); return; }
        }
    }
}
