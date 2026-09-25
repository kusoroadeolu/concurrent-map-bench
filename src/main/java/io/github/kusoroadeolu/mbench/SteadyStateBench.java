package io.github.kusoroadeolu.mbench;

import org.eclipse.collections.impl.map.mutable.ConcurrentHashMapUnsafe;
import org.jctools.maps.NonBlockingHashMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import space.vectrix.sync.collections.BucketSyncMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.GC_TYPE_ARG, JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG})
@BenchmarkMode(Mode.Throughput)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class SteadyStateBench {

    @State(Scope.Benchmark)
    public static class MapHolder {
        public Map<Long, Long> map;
        private static final int SEED = 42;

        @Param({"ConcurrentHashMap", "BucketSyncHashMap", "SynchronizedHashMap", "NonBlockingHashMap", "EclipseConcurrentHashMap"})
        public String type;

        @Param({"Prepopulate"})
        public String mode;

        @Param({"16384","131072"}) // 1 << 14 && 1 << 17
        int size;

        static final Long INITIAL = 0L;

        Long[] keys;

        @Setup(Level.Trial)
        public void setup() {
            boolean presize = mode.equalsIgnoreCase("presize");
            boolean prepopulate = mode.equalsIgnoreCase("prepopulate");

            this.map = switch(this.type) {
                case "BucketSyncHashMap" -> presize || prepopulate ? new BucketSyncMap<>(BucketSyncMap.FASTEST_SPREAD, this.size) : new BucketSyncMap<>(BucketSyncMap.FASTEST_SPREAD);
                case "ConcurrentHashMap" -> presize || prepopulate ? new ConcurrentHashMap<>(this.size) : new ConcurrentHashMap<>();
                case "NonBlockingHashMap" -> presize || prepopulate ? new NonBlockingHashMap<>(this.size) : new NonBlockingHashMap<>();
                case "SynchronizedHashMap" -> presize || prepopulate
                        ? Collections.synchronizedMap(new HashMap<>(this.size))
                        : Collections.synchronizedMap(new HashMap<>());
                case "EclipseConcurrentHashMap" -> presize || prepopulate ? new ConcurrentHashMapUnsafe<>(this.size) : new ConcurrentHashMapUnsafe<>();
                default -> throw new IllegalStateException("Unexpected value: " + this.type);
            };

            keys = new Long[size];

            SplittableRandom random = new SplittableRandom(SEED);

            if (prepopulate) {
                for(int i = 0; i < size; i++) {
                    keys[i] = random.nextLong();
                    this.map.put(keys[i], INITIAL);
                }
            }
        }
    }


    @State(Scope.Thread)
    public static class Sample {
        @Param({"0", "20", "50", "80", "100"})
        private int readPercentage;

        private long cursor = 0;
        private int length;
        private int mask;
        private boolean[] readMap;
        private long xorState;
        private Long[] keys;
        private long id;

        private static final AtomicLong ID = new AtomicLong();

        @Setup(Level.Trial)
        public void trialSetup(MapHolder holder) {
            id = ID.getAndIncrement();
            keys = Arrays.copyOf(holder.keys, holder.size);
        }

        @Setup(Level.Iteration)
        public void setup(final MapHolder holder) {
            length = holder.size;
            readMap = new boolean[length];
            mask = length - 1;
            final SplittableRandom random = new SplittableRandom(MapHolder.SEED + id);
            for(int i = 0; i < length; i++) {
                readMap[i] = random.nextInt(1,101) <= readPercentage;
            }

            xorState = random.nextLong();
            cursor = random.nextInt();
        }


        //advances the cursor
        boolean isRead() {
            return readMap[(int) (cursor++ & mask)];
        }


        //xor shift
        int next() {
            xorState ^= xorState << 13; xorState ^= xorState >>> 7; xorState ^= xorState << 17;
            return (int) ((xorState >>> 20) & mask);
        }
    }



    @Threads(8)
    @Benchmark
    public void mapBenchmark(MapHolder holder, Sample sample, Blackhole bh) {
        int next = sample.next();
        boolean isRead = sample.isRead();
        if (isRead) bh.consume(holder.map.get(sample.keys[next]));
        else bh.consume(holder.map.put(sample.keys[next], sample.keys[(int) (sample.cursor & sample.mask)]));
    }
}

/*
╭─────────── io.github.kusoroadeolu.mbench.SteadyStateBench.mapBenchmark ─────────────╮
│  Mode        ReadPercentage Size   Type                     Score   Error   Unit    │
│  ----------- -------------- ------ ------------------------ ------- ------- ------  │
│  Prepopulate 0              16384  ConcurrentHashMap        74.760  ± 1.433 ops/us  │
│  Prepopulate 0              16384  BucketSyncHashMap        64.508  ± 0.871 ops/us  │
│  Prepopulate 0              16384  SynchronizedHashMap      12.753  ± 0.233 ops/us  │
│  Prepopulate 0              16384  NonBlockingHashMap       102.447 ± 1.720 ops/us  │
│  Prepopulate 0              16384  EclipseConcurrentHashMap 75.455  ± 1.743 ops/us  │
│  Prepopulate 0              131072 ConcurrentHashMap        48.198  ± 0.811 ops/us  │
│  Prepopulate 0              131072 BucketSyncHashMap        30.878  ± 0.325 ops/us  │
│  Prepopulate 0              131072 SynchronizedHashMap      5.603   ± 0.246 ops/us  │
│  Prepopulate 0              131072 NonBlockingHashMap       47.632  ± 0.705 ops/us  │
│  Prepopulate 0              131072 EclipseConcurrentHashMap 30.692  ± 0.585 ops/us  │
│  Prepopulate 20             16384  ConcurrentHashMap        84.541  ± 1.332 ops/us  │
│  Prepopulate 20             16384  BucketSyncHashMap        103.651 ± 1.717 ops/us  │
│  Prepopulate 20             16384  SynchronizedHashMap      13.001  ± 0.206 ops/us  │
│  Prepopulate 20             16384  NonBlockingHashMap       109.483 ± 1.264 ops/us  │
│  Prepopulate 20             16384  EclipseConcurrentHashMap 86.736  ± 1.452 ops/us  │
│  Prepopulate 20             131072 ConcurrentHashMap        52.548  ± 0.940 ops/us  │
│  Prepopulate 20             131072 BucketSyncHashMap        37.079  ± 0.371 ops/us  │
│  Prepopulate 20             131072 SynchronizedHashMap      6.037   ± 0.135 ops/us  │
│  Prepopulate 20             131072 NonBlockingHashMap       50.426  ± 0.800 ops/us  │
│  Prepopulate 20             131072 EclipseConcurrentHashMap 33.780  ± 1.159 ops/us  │
│  Prepopulate 50             16384  ConcurrentHashMap        102.660 ± 5.239 ops/us  │
│  Prepopulate 50             16384  BucketSyncHashMap        117.024 ± 1.976 ops/us  │
│  Prepopulate 50             16384  SynchronizedHashMap      15.412  ± 0.337 ops/us  │
│  Prepopulate 50             16384  NonBlockingHashMap       128.739 ± 1.457 ops/us  │
│  Prepopulate 50             16384  EclipseConcurrentHashMap 105.135 ± 3.314 ops/us  │
│  Prepopulate 50             131072 ConcurrentHashMap        60.095  ± 0.733 ops/us  │
│  Prepopulate 50             131072 BucketSyncHashMap        40.110  ± 0.286 ops/us  │
│  Prepopulate 50             131072 SynchronizedHashMap      6.226   ± 0.673 ops/us  │
│  Prepopulate 50             131072 NonBlockingHashMap       55.371  ± 0.616 ops/us  │
│  Prepopulate 50             131072 EclipseConcurrentHashMap 39.516  ± 0.552 ops/us  │
│  Prepopulate 80             16384  ConcurrentHashMap        164.326 ± 2.000 ops/us  │
│  Prepopulate 80             16384  BucketSyncHashMap        144.436 ± 2.505 ops/us  │
│  Prepopulate 80             16384  SynchronizedHashMap      16.464  ± 0.237 ops/us  │
│  Prepopulate 80             16384  NonBlockingHashMap       161.228 ± 2.353  ops/us │
│  Prepopulate 80             16384  EclipseConcurrentHashMap 144.348 ± 3.581 ops/us  │
│  Prepopulate 80             131072 ConcurrentHashMap        71.931  ± 0.838 ops/us  │
│  Prepopulate 80             131072 BucketSyncHashMap        44.333  ± 0.388 ops/us  │
│  Prepopulate 80             131072 SynchronizedHashMap      7.178   ± 0.115 ops/us  │
│  Prepopulate 80             131072 NonBlockingHashMap       61.571  ± 0.720 ops/us  │
│  Prepopulate 80             131072 EclipseConcurrentHashMap 47.077  ± 0.803 ops/us  │
│  Prepopulate 100            16384  ConcurrentHashMap        246.386 ± 4.590 ops/us  │
│  Prepopulate 100            16384  BucketSyncHashMap        174.069 ± 1.856 ops/us  │
│  Prepopulate 100            16384  SynchronizedHashMap      17.262  ± 1.349 ops/us  │
│  Prepopulate 100            16384  NonBlockingHashMap       224.179 ± 2.164 ops/us  │
│  Prepopulate 100            16384  EclipseConcurrentHashMap 239.558 ± 3.024 ops/us  │
│  Prepopulate 100            131072 ConcurrentHashMap        82.748  ± 0.574 ops/us  │
│  Prepopulate 100            131072 BucketSyncHashMap        46.786  ± 1.497 ops/us  │
│  Prepopulate 100            131072 SynchronizedHashMap      8.485   ± 0.276 ops/us  │
│  Prepopulate 100            131072 NonBlockingHashMap       71.996  ± 2.182 ops/us  │
│  Prepopulate 100            131072 EclipseConcurrentHashMap 88.997  ± 2.596 ops/us  │
╰─────────────────────────────────────────────────────────────────────────────────────╯
* */
