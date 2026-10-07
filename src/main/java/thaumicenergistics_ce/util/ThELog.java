package thaumicenergistics_ce.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 持有 mod 日志器，让写日志行的类不必导入
 * {@code @Mod} 组合根。
 * 是普通持有者而非包装器：{@link Logger} 实例与传给
 * {@link LoggerFactory#getLogger(String)} 的名称与根类用的完全一致，因此日志输出不变。
 */
public final class ThELog {
    private ThELog() {}

    public static final Logger LOG = LoggerFactory.getLogger("ThaumicEnergistics");
}