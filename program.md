# KCC14 — Autonomous Warehouse Optimization

This is an experiment to autonomously optimize a Java warehouse simulation (KNAPP Coding Contest 14). 25 AeroBots pick 10,000 orders from a 30×10 rack grid (20 levels each), minimizing total cost.

## Setup

To set up a new experiment:

1. **Agree on a run tag**: propose a tag based on today's date (e.g. `mar26`). The branch `autoresearch/<tag>` must not already exist.
2. **Create the branch**: `git checkout -b autoresearch/<tag>` from current master.
3. **Read the in-scope files**:
   - `input/KCC14/src/com/knapp/codingcontest/solution/Solution.java` — **the file you modify**. Your solution logic goes here.
   - `input/KCC14/src/com/knapp/codingcontest/Main.java` — entry point, prints cost results. **Do not modify.**
   - `input/KCC14/src/com/knapp/codingcontest/MainCostFactors.java` — cost weights (500/unfinished order, 1/tick). **Do not modify.**
   - `input/KCC14/src/com/knapp/codingcontest/operations/` — all operation interfaces: `Warehouse.java`, `AeroBot.java`, `PickArea.java`, `ChargingArea.java`, `ParkingArea.java`, `CostFactors.java`, `InfoSnapshot.java`, `Operation.java`. **Do not modify.**
   - `input/KCC14/src/com/knapp/codingcontest/data/` — data model classes: `Order.java`, `Container.java`, `Rack.java`, `Waypoint.java`, `Location.java`, `Institute.java`. **Do not modify.**
   - `input/KCC14/src/com/knapp/codingcontest/core/` — internal simulation engine. **Do not modify.**
   - `input/KCC14/data/warehouse.properties` — warehouse configuration. **Do not modify.**
   - `input/KCC14/data/order-lines.csv` — 10,000 orders. **Do not modify.**
   - `input/KCC14/data/product-containers.csv` — 5,100 containers with positions. **Do not modify.**
   - `eval.sh` — build & run script. **Do not modify.**
4. **Verify prerequisites**: Check that `java -version` works (use `/opt/homebrew/opt/openjdk/bin/java` if not on PATH). Check that data files exist in `input/KCC14/data/`. If Java is not installed, tell the human.
5. **Initialize results.tsv**: Create `results.tsv` with the header row. The baseline will be the first run.
6. **Confirm and go**: Confirm setup looks good.

Once you get confirmation, kick off the experimentation.

## Experimentation

### The Problem

The warehouse has:
- **25 AeroBots** starting at parking area (0,0)
- **30×10 rack grid** (300 racks), each with **20 levels** = 6,000 storage locations
- **5,100 containers** stored across those locations, each holding one product type (unlimited quantity)
- **10,000 orders** to pick, each requiring one specific product
- **15 pick stations** at the pick area — only orders currently at pick stations can be picked
- **10 charging slots** — AeroBots have charge that depletes and must be recharged
- Costs per tick for operations: MoveH=3 ticks, MoveV=4 ticks/level, Load=2, Store=2, Pick=1, Charge=1/unit-restored, Idle=1
- Charge costs: MoveH=4/step, MoveV=8/level, Load=2, Store=2, Pick=2, Idle=1, max=20000, charge rate=100/tick

### The AeroBot Workflow

Each bot cycle: **park → move to rack → climb to level → load container → climb down → move to pick area → pick order → move back to rack → climb → store container → climb down → repeat** (with charging when needed).

### Key API Methods

```java
// Navigation & actions (on AeroBot)
aeroBot.planMoveToWaypoint(waypoint);   // move horizontally
aeroBot.planClimbToLevel(level);         // climb up/down at rack
aeroBot.planLoadContainer(container);    // pick up container from rack
aeroBot.planPick(order);                 // fulfill order at pick area
aeroBot.planStoreContainer();            // put container back in rack
aeroBot.planStartCharge();               // charge at charging area

// Queries (on Warehouse)
warehouse.findAvailableContainers("productCode");  // find containers for a product
warehouse.findEmptyRackStorageLocations();          // find empty spots to store
warehouse.getOpenOrders();                           // remaining orders
warehouse.getPickArea().getCurrentOrders();          // orders at pick stations now
warehouse.executeTicksUntilFirstBotToFinish();       // advance simulation
warehouse.executeOneTick();                          // advance one tick

// Cost estimation (on Warehouse)
warehouse.calculateMoveToWaypoint(from, to);  // estimate ticks & charge
warehouse.calculateClimbToLevel(from, to);     // estimate climbing cost
```

**IMPORTANT**: After planning operations, you MUST call `executeOneTick()` or `executeTicksUntilFirstBotToFinish()` to actually advance the simulation. Operations do NOT execute automatically.

**What you CAN do:**
- Modify `input/KCC14/src/com/knapp/codingcontest/solution/Solution.java` — this is the only file you edit. Everything is fair game: algorithm, data structures, heuristics, bot assignment strategy, order scheduling, charging policy, container reuse.
- Add any imports from java standard library.
- Create helper methods, inner classes, data structures — all within Solution.java.

**What you CANNOT do:**
- Modify any other Java file. All files in `operations/`, `data/`, `core/`, `Main.java`, `MainCostFactors.java` are read-only.
- Modify `eval.sh`. It is the frozen build/run harness.
- Modify data files (`warehouse.properties`, CSVs). They are the ground truth.
- Install new dependencies or packages. Standard Java library only.

**The goal is simple: get the lowest total_cost.**

```
total_cost = (unfinished_orders × 500) + (ticks_runtime × 1)
```

Finishing all 10,000 orders is critical (each unfinished = 500 cost), but doing it in fewer ticks also matters.

**Simplicity criterion**: All else being equal, simpler is better. A small improvement that adds ugly complexity is not worth it. Conversely, removing something and getting equal or better results is a great outcome.

**The first run**: Establish baseline first. Note: the empty Solution.java (no `run()` body) will produce cost = 5,000,000 (all orders unfinished). Your first real experiment should implement basic order picking to get a real baseline.

## Output format

Once the evaluation finishes, `Main.java` prints results like:

```
  ===================================== : ============ | ======================
      what                              :       costs  |  (details: count,...)
  ------------------------------------- : ------------ | ----------------------
   -> costs/unfinished orders           :      1500.0  |       3
   -> costs ticks runtime               :      8542.0  |      8542
  ------------------------------------- : ------------ | ----------------------
   => TOTAL COST                            10042.0
                                          ============
```

The eval.sh script appends parseable metric lines:

```
---
total_cost: 10042.0
ticks: 8542
unfinished_orders: 3
```

Extract the key metric:

```
grep "^total_cost:" run.log
```

## Logging results

When an experiment is done, log it to `results.tsv` (tab-separated, NOT comma-separated).

The TSV has a header row and 5 columns:

```
commit	total_cost	ticks	status	description
```

1. git commit hash (short, 7 chars)
2. total_cost achieved (e.g. 10042.0) — use 0.0 for crashes
3. ticks runtime (integer) — use 0 for crashes
4. status: `keep`, `discard`, or `crash`
5. short text description of what this experiment tried

Example:

```
commit	total_cost	ticks	status	description
a1b2c3d	5000000.0	0	keep	baseline (empty solution)
b2c3d4e	152340.0	42340	keep	basic greedy order picking
c3d4e5f	165000.0	45000	discard	randomized bot assignment
d4e5f6g	0.0	0	crash	null pointer in order scheduling
```

## The experiment loop

The experiment runs on a dedicated branch (e.g. `autoresearch/mar26`).

LOOP FOREVER:

1. Look at the git state: the current branch/commit we're on
2. Modify `input/KCC14/src/com/knapp/codingcontest/solution/Solution.java` with an experimental idea.
3. `git add input/KCC14/src/com/knapp/codingcontest/solution/Solution.java && git commit -m "experiment: <description>"`
4. Run the experiment: `bash eval.sh > run.log 2>&1` (redirect everything — do NOT use tee or let output flood your context)
5. Read out the results: `grep "^total_cost:\|TOTAL COST" run.log`
6. If the grep output is empty, the run crashed. Run `tail -n 50 run.log` to read the Java stack trace and attempt a fix.
7. Record the results in the tsv (NOTE: do not commit the results.tsv file, leave it untracked by git)
8. If total_cost improved (lower), you "advance" the branch, keeping the git commit
9. If total_cost is equal or worse, record the discard commit hash, then `git reset --hard <previous kept commit>` to discard it cleanly

**Timeout**: Each experiment should take ~30-60 seconds (compile + run). If a run exceeds 5 minutes, kill it and treat as failure.

**Crashes**: If a run crashes (compilation error, NullPointerException, etc.), use your judgment: If it's something dumb and easy to fix (typo, missing import, wrong method call), fix it and re-run. If the idea is fundamentally broken, skip it, log "crash", and move on.

**NEVER STOP**: Once the experiment loop has begun (after the initial setup), do NOT pause to ask the human if you should continue. Do NOT ask "should I keep going?" or "is this a good stopping point?". The human might be asleep, or gone from a computer and expects you to continue working *indefinitely* until you are manually stopped. You are autonomous. If you run out of ideas, think harder — re-read the API files for new methods, try combining previous near-misses, try radically different strategies. The loop runs until the human interrupts you, period.

## Strategy hints

### Phase 1: Get a working solution
- Start with a simple greedy approach: for each available order at pick stations, find the nearest available container, assign a bot to fetch it
- Use `warehouse.executeTicksUntilFirstBotToFinish()` to advance simulation efficiently
- Handle the `getParticipantName()` and `getParticipantInstitution()` stubs first (they throw if null)

### Phase 2: Optimize order scheduling
- Not all orders are at pick stations simultaneously (only 15 at a time). Plan which containers to pre-fetch
- Match bots to the closest containers for efficiency (minimize travel distance)
- Consider which orders are coming next and pre-position containers near the pick area

### Phase 3: Optimize logistics
- **Charging strategy**: Don't let bots run out of charge. Plan charging proactively when charge gets low
- **Container reuse**: If the same product is needed multiple times, keep the container loaded or store it close to pick area
- **Parallel bot usage**: Coordinate all 25 bots to work concurrently, not sequentially
- **Rack proximity**: Prefer containers closer to the pick area (shorter travel = fewer ticks)
- **Multi-bot coordination**: Avoid rack conflicts (only one bot can climb a rack at a time)

### Phase 4: Advanced optimizations
- Batch order planning: queue multiple fetch-pick cycles per bot before executing
- Near-pick-area storage: store frequently-used containers in racks near the pick area
- Dynamic bot allocation: idle bots pre-fetch containers for upcoming orders
- Minimize climb distance: prefer lower-level containers (MoveV costs 4 ticks/level vs MoveH costs 3 ticks/step)
- Calculate cost estimates with `warehouse.calculateMoveToWaypoint()` etc. to choose optimal assignments

### Warehouse layout reference
- Parking: (0,0)
- Charging: origin (2,5), 10 slots
- Pick area: origin (5,2), 15 stations
- Racks: origin (5,5), grid 30×10, offset 1×1 — so racks span roughly from (5,5) to (35,15)
- Each rack has 20 levels
