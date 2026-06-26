package com.nfclock.plugin;

/**
 * 充电反馈：根据电量增量播放蜂鸣（与 xhky control NFCLockRSSIManager + getRSSILevel 一致）
 */
public class NFCLockChargeFeedback {

    private final FrequencyBeeper beeper = new FrequencyBeeper();
    private int lastPowerLevel = 0;
    private int lastDiffValue = 0;

    public void reset() {
        lastPowerLevel = 0;
        lastDiffValue = 0;
        beeper.stopBeeping();
    }

    public void update(String powerLevelStr) {
        NFCLockRSSILevel level = resolveLevel(powerLevelStr);
        if (level != null) {
            beeper.startBeeping(level);
        }
    }

    public void stop() {
        beeper.stopBeeping();
    }

    public void release() {
        beeper.release();
    }

    private NFCLockRSSILevel resolveLevel(String powerLevelStr) {
        int powerPercent = parsePowerPercent(powerLevelStr);
        int diff = powerPercent - lastPowerLevel;
        lastPowerLevel = powerPercent;

        if (diff == 100 || (powerPercent == 100 && diff == 0)) {
            return null;
        }

        if (diff < lastDiffValue && powerPercent == 100) {
            diff = lastDiffValue;
        } else {
            lastDiffValue = diff;
        }

        if (diff > 26) {
            return NFCLockRSSILevel.LEVEL5;
        }
        if (diff > 18) {
            return NFCLockRSSILevel.LEVEL4;
        }
        if (diff > 12) {
            return NFCLockRSSILevel.LEVEL3;
        }
        if (diff > 6) {
            return NFCLockRSSILevel.LEVEL2;
        }
        return NFCLockRSSILevel.LEVEL1;
    }

    private int parsePowerPercent(String powerLevel) {
        if (powerLevel == null || powerLevel.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(powerLevel.trim().replace("%", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
