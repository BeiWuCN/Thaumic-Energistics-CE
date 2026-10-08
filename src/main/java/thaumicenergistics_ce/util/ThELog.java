package thaumicenergistics_ce.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 持有 mod 日志器，写日志的类不必导入 {@code @Mod} 组合根。
 * 是普通持有者不是包装器：{@link Logger} 实例和传给 {@link LoggerFactory#getLogger(String)} 的名字
 * 与根类用的完全一致，日志输出不变。
 */
public final class ThELog {
    private ThELog() {}

    public static final Logger LOG = LoggerFactory.getLogger("ThaumicEnergistics");
}