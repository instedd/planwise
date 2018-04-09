package planwise.engine;

public class Algorithm {
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
}
