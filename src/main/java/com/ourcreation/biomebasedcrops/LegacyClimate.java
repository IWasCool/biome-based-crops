package com.ourcreation.biomebasedcrops;

/** Conversion boundary for old saves and optional mods that still expose numeric temperatures. */
public final class LegacyClimate {
    private LegacyClimate() { }

    public static CropClimate fromCelsius(float value) {
        if (!Float.isFinite(value)) return CropClimate.NEUTRAL;
        if (value <= 0) return CropClimate.ICY;
        if (value <= 10) return CropClimate.COLD;
        if (value <= 25) return CropClimate.NEUTRAL;
        return value <= 35 ? CropClimate.WARM : CropClimate.HOT;
    }

    public static float toCelsius(CropClimate climate) {
        return switch (climate) {
            case ICY -> -10;
            case COLD -> 5;
            case NEUTRAL -> 20;
            case WARM -> 30;
            case HOT -> 45;
        };
    }
}
