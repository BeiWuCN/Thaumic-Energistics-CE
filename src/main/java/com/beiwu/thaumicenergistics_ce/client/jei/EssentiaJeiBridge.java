package com.beiwu.thaumicenergistics_ce.client.jei;

import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/**
 * 只在装了 AE2 JEI 整合时才会被加载的一小段：把要素转换器登记进去。
 *
 * <p>单独一个类是有意的——{@code ae2jeiintegration} 不是硬依赖，
 * 直接在新版插件里引用它的类会在没装这个整合的整合包里抛 {@code NoClassDefFoundError}。
 * 调用点先用 mod 载入检查挡住，JVM 就不会解析到这个类。
 */
final class EssentiaJeiBridge {

    private EssentiaJeiBridge() {}

    static void register() {
        IngredientConverters.register(EssentiaIngredientConverter.INSTANCE);
    }
}
