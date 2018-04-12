package planwise.engine;

import java.util.Arrays;

public class Algorithm {
    public static long countPopulation(float[] popData,
                                       float popNodata) {
        float sum = 0.0f;
        for (int i = 0; i < popData.length; i++) {
            if (popData[i] != popNodata)
                sum += popData[i];
        }
        return (long) sum;
    }

    public static void multiplyPopulation(float[] popData,
                                          float popNodata,
                                          float factor) {
        for (int i = 0; i < popData.length; i++) {
            if (popData[i] != popNodata)
                popData[i] *= factor;
        }
    }

    public static long countPopulationUnderCoverage(float[] popData,
                                                    int popStride,
                                                    float popNodata,
                                                    byte[] covData,
                                                    int covStride,
                                                    byte covNodata,
                                                    int popLeft,
                                                    int popTop,
                                                    int popRight,
                                                    int popBottom,
                                                    int covLeft,
                                                    int covTop) {
        float sum = 0.0f;
        int width = popRight - popLeft + 1;
        int popSkip = popStride - width;
        int covSkip = covStride - width;
        int popIdx = popTop * popStride + popLeft;
        int covIdx = covTop * covStride + covLeft;

        for (int y = popTop; y <= popBottom; y++) {
            for (int x = popLeft; x <= popRight; x++) {
                if (popData[popIdx] != popNodata && covData[covIdx] != covNodata) {
                    sum += popData[popIdx];
                }
                popIdx++;
                covIdx++;
            }
            popIdx += popSkip;
            covIdx += covSkip;
        }
        return (long) sum;
    }

    public static void multiplyPopulationUnderCoverage(float[] popData,
                                                       int popStride,
                                                       float popNodata,
                                                       byte[] covData,
                                                       int covStride,
                                                       byte covNodata,
                                                       float factor,
                                                       int popLeft,
                                                       int popTop,
                                                       int popRight,
                                                       int popBottom,
                                                       int covLeft,
                                                       int covTop) {
        int width = popRight - popLeft + 1;
        int popSkip = popStride - width;
        int covSkip = covStride - width;
        int popIdx = popTop * popStride + popLeft;
        int covIdx = covTop * covStride + covLeft;

        for (int y = popTop; y <= popBottom; y++) {
            for (int x = popLeft; x <= popRight; x++) {
                if (popData[popIdx] != popNodata && covData[covIdx] != covNodata) {
                    popData[popIdx] *= factor;
                }
                popIdx++;
                covIdx++;
            }
            popIdx += popSkip;
            covIdx += covSkip;
        }
    }

    public static float[] computeExactQuartiles(float[] data, float nodata) {
        float copy[] = new float[data.length];
        int j = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != nodata) {
                copy[j++] = data[i];
            }
        }
        Arrays.sort(copy, 0, j);
        return new float[]{ copy[0], copy[j/4], copy[j/2], copy[3*j/4], copy[j-1] };
    }

    public static void mapDataForRender(float[] popData,
                                        float popNodata,
                                        byte[] renderData,
                                        byte renderNodata,
                                        float[] quartiles) {
        if (quartiles.length < 1 || quartiles.length > 128) {
            throw new IllegalArgumentException("quartiles array has invalid size");
        }
        for (int i = 0; i < popData.length; i++) {
            float value = popData[i];
            byte renderValue = 0;
            if (value == popNodata) {
                renderValue = renderNodata;
            } else {
                while (renderValue < quartiles.length && value > quartiles[renderValue]) {
                    renderValue++;
                }
            }
            renderData[i] = renderValue;
        }
    }
}
