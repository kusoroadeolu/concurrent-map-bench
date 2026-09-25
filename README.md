# Map Bench
A project benchmarking different JVM based concurrent hash maps using JMH. The available benchmark measures the read throughput scaling as the read ratio increases (0/20/50/80/100) at a fixed thread count and pre-initialized keys
to avoid boxing during the benchmark. 

This benchmark was measured across five implementations
1. The JDK concurrent hashmap
2. A synchronized hashmap (JDK single threaded hashmap)
3. Cliff Click's non-blocking hashmap. [Source](https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/maps/NonBlockingHashMap.java)
4. Eclipse Collection concurrent hashmap. [Source](https://github.com/eclipse-collections/eclipse-collections/blob/master/eclipse-collections/src/main/java/org/eclipse/collections/impl/map/mutable/ConcurrentHashMapUnsafe.java)
5. Vectrix Sync Bucket hashmap. [Source](https://github.com/vectrix-space/sync)

## Benchmark setup

- **Threads:** 8 (thread scaling is out of scope)
- **Map sizes:** 2^14 (16,384) and 2^17 (131,072) entries, pre-populated with random `Long` keys
- **Read ratios:** 0 / 20 / 50 / 80 / 100 %
- **Operations:** `get` for reads, `put` for writes, on pre-generated boxed keys
- **JMH:** 5 warmup iterations, 10 measurement iterations, 2 forks
- **Unit:** ops/µs (higher is better)

### Environment

| |                        |
|---|------------------------|
| CPU | Intel i5               |
| Cores / threads | 4 cores - 8 processors |
| RAM | 16GB                   |
| OS | Windows 11             |
| JDK | 25 (Open JDK)          |
| GC / heap flags | -Xms8g, -Xmx8g, -XX:+UseG1GC       |

## Results

### 16,384 entries

| Read % | ConcurrentHashMap | BucketSyncMap | SynchronizedMap | NonBlockingHashMap | Eclipse CHMUnsafe |
|-------:|------------------:|--------------:|----------------:|-------------------:|------------------:|
| 0   | 74.76  | 64.51  | 12.75 | **102.45** | 75.46  |
| 20  | 84.54  | 103.65 | 13.00 | **109.48** | 86.74  |
| 50  | 102.66 | 117.02 | 15.41 | **128.74** | 105.14 |
| 80  | **164.33** | 144.44 | 16.46 | 161.23 | 144.35 |
| 100 | **246.39** | 174.07 | 17.26 | 224.18 | 239.56 |

### 131,072 entries

| Read % | ConcurrentHashMap | BucketSyncMap | SynchronizedMap | NonBlockingHashMap | Eclipse CHMUnsafe |
|-------:|------------------:|--------------:|----------------:|-------------------:|------------------:|
| 0   | **48.20** | 30.88 | 5.60 | 47.63 | 30.69 |
| 20  | **52.55** | 37.08 | 6.04 | 50.43 | 33.78 |
| 50  | **60.10** | 40.11 | 6.23 | 55.37 | 39.52 |
| 80  | **71.93** | 44.33 | 7.18 | 61.57 | 47.08 |
| 100 | 82.75 | 46.79 | 8.49 | 72.00 | **89.00** |

Scores are means (in microseconds); per-cell error margins (mostly under 3%) are in the raw JMH output;

### Takeaways

- Every map is much slower at 131K than at 16K, most likely because the working set no longer fits in cache.
- At 16K, NonBlockingHashMap leads on write-heavy mixes, and ConcurrentHashMap takes over at 80%+ reads (at 80% the two are within error of each other).
- At 131K, ConcurrentHashMap leads until pure reads, where Eclipse edges it out, though Eclipse is the worst of the set when writes dominate 
- The synchronized map is roughly 5-10x slower than the rest at every ratio, because reads and writes are serialized on one lock.


## Running the benchmarks
To run the benchmarks for yourself you need to be on JDK 28, though this can run on JDK 25

```bash
mvn clean package
```

```bash
java -jar target/benchmark.jar SteadyStateBench
```

## License 
MIT

