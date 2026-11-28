package com.jobbeacon.utils.dedup;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.SplittableRandom;

public final class MinHasher {

    public static final int NUM_HASHES = 128;
    public static final int BANDS = 32;
    public static final int ROWS = 4;
    public static final int SHINGLE = 5;

    private static final long MERSENNE_61 = (1L << 61) - 1;
    private static final long SEED = 0x4A6F62426561636FL;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long[] A = new long[NUM_HASHES];
    private static final long[] B = new long[NUM_HASHES];

    static {
        SplittableRandom random = new SplittableRandom(SEED);
        for (int i = 0; i < NUM_HASHES; i++) {
            A[i] = 1 + random.nextLong(MERSENNE_61 - 1);
            B[i] = random.nextLong(MERSENNE_61);
        }
    }

    private MinHasher() {
    }

    public static int[] signature(String text) {
        long[] min = new long[NUM_HASHES];
        Arrays.fill(min, Long.MAX_VALUE);
        String folded = ContentHasher.normalize(text);
        if (!folded.isEmpty()) {
            String[] tokens = folded.split(" ");
            int count = Math.max(1, tokens.length - SHINGLE + 1);
            for (int start = 0; start < count; start++) {
                int end = Math.min(tokens.length, start + SHINGLE);
                long x = Math.floorMod(fnv1a(String.join(" ", Arrays.copyOfRange(tokens, start, end))), MERSENNE_61);
                for (int i = 0; i < NUM_HASHES; i++) {
                    long h = mod61(mulMod61(A[i], x) + B[i]);
                    if (h < min[i]) {
                        min[i] = h;
                    }
                }
            }
        }
        int[] sig = new int[NUM_HASHES];
        for (int i = 0; i < NUM_HASHES; i++) {
            sig[i] = (int) (min[i] ^ (min[i] >>> 32));
        }
        return sig;
    }

    public static long[] bands(int[] sig) {
        requireLength(sig);
        long[] result = new long[BANDS];
        for (int band = 0; band < BANDS; band++) {
            long h = FNV_OFFSET ^ band;
            h *= FNV_PRIME;
            for (int r = 0; r < ROWS; r++) {
                int v = sig[band * ROWS + r];
                for (int shift = 0; shift < 32; shift += 8) {
                    h ^= (v >>> shift) & 0xff;
                    h *= FNV_PRIME;
                }
            }
            result[band] = h;
        }
        return result;
    }

    public static double estimateJaccard(int[] a, int[] b) {
        requireLength(a);
        requireLength(b);
        int equal = 0;
        for (int i = 0; i < NUM_HASHES; i++) {
            if (a[i] == b[i]) {
                equal++;
            }
        }
        return (double) equal / NUM_HASHES;
    }

    private static void requireLength(int[] sig) {
        if (sig == null || sig.length != NUM_HASHES) {
            throw new IllegalArgumentException("signature must have " + NUM_HASHES + " values");
        }
    }

    private static long fnv1a(String s) {
        long h = FNV_OFFSET;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= FNV_PRIME;
        }
        return h;
    }

    private static long mulMod61(long a, long b) {
        long hi = Math.multiplyHigh(a, b);
        long lo = a * b;
        long low61 = lo & MERSENNE_61;
        long high = (lo >>> 61) | (hi << 3);
        return mod61(low61 + high);
    }

    private static long mod61(long x) {
        long r = (x & MERSENNE_61) + (x >>> 61);
        return r >= MERSENNE_61 ? r - MERSENNE_61 : r;
    }
}
