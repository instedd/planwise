package planwise.engine;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import net.mintern.primitive.Primitive;
import net.mintern.primitive.comparators.IntComparator;

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

    public static int[] getPointsOfCoverage(float[] popData,
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
                                            int covTop){

        int width = popRight - popLeft + 1;
        int height = popTop - popBottom + 1;
        int popSkip = popStride - width;
        int covSkip = covStride - width;
        int popIdx = popTop * popStride + popLeft;
        int covIdx = covTop * covStride + covLeft;

        int [] ind = new int[popData.length];
        int j = 0;

        for (int y = popTop; y <= popBottom; y++) {
            for (int x = popLeft; x <= popRight; x++) {
                if (popData[popIdx] != popNodata && covData[covIdx] != covNodata) {
                    ind[j]= popIdx;
                    j ++;
                }
                popIdx++;
                covIdx++;
            }
            popIdx += popSkip;
            covIdx += covSkip;
        }

        int[] res = Arrays.copyOfRange(ind, 0, j);
        return res;
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

    public static void markPixelsUnderCoverage(byte[] dstData,
                                               int dstStride,
                                               float dstNodata,
                                               byte[] srcData,
                                               int srcStride,
                                               byte srcNodata,
                                               byte markValue,
                                               int dstLeft,
                                               int dstTop,
                                               int dstRight,
                                               int dstBottom,
                                               int srcLeft,
                                               int srcTop) {
        int width = dstRight - dstLeft + 1;
        int dstSkip = dstStride - width;
        int srcSkip = srcStride - width;
        int dstIdx = dstTop * dstStride + dstLeft;
        int srcIdx = srcTop * srcStride + srcLeft;

        for (int y = dstTop; y <= dstBottom; y++) {
            for (int x = dstLeft; x <= dstRight; x++) {
                if (dstData[dstIdx] != dstNodata && srcData[srcIdx] != srcNodata) {
                    dstData[dstIdx] = markValue;
                }
                dstIdx++;
                srcIdx++;
            }
            dstIdx += dstSkip;
            srcIdx += srcSkip;
        }
    }

    public static void buildMask(byte[] dstData,
                                 float[] srcData,
                                 float srcNodata,
                                 byte maskValue,
                                 float effectiveThreshold,
                                 int stride,
                                 int left,
                                 int top,
                                 int right,
                                 int bottom) {
        int width = right - left + 1;
        int skip = stride - width;
        int index = top * stride + left;

        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                if (srcData[index] != srcNodata && srcData[index] > effectiveThreshold) {
                    dstData[index] = maskValue;
                }
                index++;
            }
            index += skip;
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

    public static int findMaxIndex(float[] values,
                                   float nodata) {
        int found = -1;
        float maxValue = nodata;

        for (int i = 0; i < values.length; i++) {
            if (values[i] != nodata && (values[i] > maxValue || maxValue == nodata)) {
                found = i;
                maxValue = values[i];
            }
        }
        return found;
    }

    public static int[] filterAndSortIndices(float[] values,
                                             float nodata,
                                             float cutoff) {
        int indices[] = new int[values.length];
        int j = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != nodata && values[i] > cutoff) {
                indices[j++] = i;
            }
        }
        IntComparator valueCmp = (i1, i2) -> Float.compare(values[i1], values[i2]);
        Primitive.sort(indices, 0, j, valueCmp);
        return Arrays.copyOf(indices, j);
    }

    public static List<float[]> locateIndices(float[] values,
                                              int[] indices,
                                              int xsize,
                                              double[] geotransform) {
        List<float[]> locations = new ArrayList<>(indices.length);
        for (int i = 0; i < indices.length; i++) {
            int index = indices[i];
            int x = index % xsize;
            int y = (int) Math.floor(index / xsize);
            double longitude = geotransform[0] + x * geotransform[1];
            double latitude = geotransform[3] + y * geotransform[5];
            float location[] = new float[3];
            location[0] = (float) longitude;
            location[1] = (float) latitude;
            location[2] = values[index];
            locations.add(location);
        }
        return locations;
    }
}
