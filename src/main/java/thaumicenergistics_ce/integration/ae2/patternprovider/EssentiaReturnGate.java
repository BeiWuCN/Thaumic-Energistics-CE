package thaumicenergistics_ce.integration.ae2.patternprovider;

import java.util.function.BooleanSupplier;

public interface EssentiaReturnGate {
    void setEssentiaGate(BooleanSupplier enabled);
    void setEssentiaChangeListener(Runnable listener);
    void notifyEssentiaChange();
}
