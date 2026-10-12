package thaumicenergistics_ce.integration.ae2.patternprovider;

import appeng.api.inventories.InternalInventory;

public interface EssentiaPatternProvider {
    InternalInventory essentiaCardInventory();
    boolean essentiaEnabled();
    boolean essentiaSupportEnabled();
    com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage essentiaReturnStorage();
}
