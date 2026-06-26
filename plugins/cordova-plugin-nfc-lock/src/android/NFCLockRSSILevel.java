package com.nfclock.plugin;

/**
 * 充电进度档位（与 xhky control 一致，由电量增量映射）
 */
public enum NFCLockRSSILevel {
    LEVEL1(1),
    LEVEL2(2),
    LEVEL3(3),
    LEVEL4(4),
    LEVEL5(5);

    private final int level;

    NFCLockRSSILevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
