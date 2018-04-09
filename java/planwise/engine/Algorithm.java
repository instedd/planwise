package planwise.engine;

public class Algorithm {
    public static float countPopulation(float[] popData,
                                        float popNodata) {
        float sum = 0.0f;
        for (int i = 0; i < popData.length; i++) {
            if (popData[i] != popNodata)
                sum += popData[i];
        }
        return sum;
    }

    public static void multiplyPopulation(float[] popData,
                                          float popNodata,
                                          float factor) {
        for (int i = 0; i < popData.length; i++) {
            if (popData[i] != popNodata)
                popData[i] *= factor;
        }
    }

    public static float countPopulationUnderCoverage(float[] popData,
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
        return sum;
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
}
