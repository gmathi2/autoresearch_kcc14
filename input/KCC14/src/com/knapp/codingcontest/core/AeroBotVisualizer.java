/* -*- java -*-
# =========================================================================== #
#                                                                             #
#                         Copyright (C) KNAPP AG                              #
#                                                                             #
#       The copyright to the computer program(s) herein is the property       #
#       of Knapp.  The program(s) may be used   and/or copied only with       #
#       the  written permission of  Knapp  or in  accordance  with  the       #
#       terms and conditions stipulated in the agreement/contract under       #
#       which the program(s) have been supplied.                              #
#                                                                             #
# =========================================================================== #
*/

package com.knapp.codingcontest.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.knapp.codingcontest.data.Waypoint;

public interface AeroBotVisualizer extends com.knapp.codingcontest.operations.AeroBotVisualizer {
  /**
   * Record the state of all AeroBots at a given tick.
   * Only samples periodically for performance.
   */
  void recordTick(long tick, Map<String, AeroBotInternal> aeroBots);

  // ----------------------------------------------------------------------------

  static class NoOpVisualizer implements AeroBotVisualizer {
    @Override
    public void recordTick(final long tick, final Map<String, AeroBotInternal> aeroBots) {
    }

    @Override
    public void generateHTML(final String filename) throws IOException {
    }

    @Override
    public boolean hasConflicts() {
      return false;
    }

    @Override
    public int getConflictCount() {
      return 0;
    }

    @Override
    public long getFirstConflictTick() {
      return 0;
    }
  }

  // ----------------------------------------------------------------------------

  static class DefaultVisualizer implements AeroBotVisualizer {
    private static final int SAMPLE_INTERVAL = 1;
    private static final int CHUNK_SIZE_TICKS = Integer.parseInt(System.getProperty("visualizerChunkSize", "25000"));

    private final Map<String, ActiveInterval> activeIntervals = new HashMap<>();
    private final Map<String, List<IntervalRecord>> intervalsByBot = new TreeMap<>();
    private final List<Conflict> conflicts = new ArrayList<>();
    private long lastTick = 0;
    private int tickCounter = 0;

    static class IntervalState {
      String kind;
      String source;
      String target;
      int fromLevel;
      int toLevel;
      boolean waiting;
      int taskId;
      String fromArea;
      String toArea;

      boolean sameAs(final IntervalState other) {
        if (other == null) {
          return false;
        }
        return (waiting == other.waiting)
            && (taskId == other.taskId)
            && (fromLevel == other.fromLevel)
            && (toLevel == other.toLevel)
            && kind.equals(other.kind)
            && source.equals(other.source)
            && target.equals(other.target);
      }
    }

    static class ActiveInterval {
      long startTick;
      IntervalState state;

      ActiveInterval(final long startTick, final IntervalState state) {
        this.startTick = startTick;
        this.state = state;
      }
    }

    static class IntervalRecord {
      long startTick;
      long endTick;
      IntervalState state;

      IntervalRecord(final long startTick, final long endTick, final IntervalState state) {
        this.startTick = startTick;
        this.endTick = endTick;
        this.state = state;
      }
    }

    static class Conflict {
      long tick;
      String rackCode;
      List<String> bots = new ArrayList<>();
      Map<String, Integer> botLevels = new HashMap<>();

      Conflict(final long tick, final String rackCode) {
        this.tick = tick;
        this.rackCode = rackCode;
      }
    }

    @Override
    public void recordTick(final long tick, final Map<String, AeroBotInternal> aeroBots) {
      lastTick = tick;
      tickCounter++;

      if ((tickCounter % DefaultVisualizer.SAMPLE_INTERVAL) != 0) {
        return;
      }

      final Map<String, List<AeroBotInternal>> botsByWaypoint = new HashMap<>();

      for (final AeroBotInternal bot : aeroBots.values()) {
        final String botCode = bot.getCode();
        final IntervalState state = buildState(bot, tick);
        final ActiveInterval active = activeIntervals.get(botCode);
        if (active == null) {
          activeIntervals.put(botCode, new ActiveInterval(tick, state));
        } else if (!active.state.sameAs(state)) {
          appendInterval(botCode, active.startTick, tick - 1, active.state);
          activeIntervals.put(botCode, new ActiveInterval(tick, state));
        }

        final Waypoint wp = bot.getCurrentWaypoint();
        final String wpCode = (wp != null) ? wp.getCode() : "null";
        if ((wp != null) && (wp.getType() == Waypoint.Type.Rack)) {
          botsByWaypoint.computeIfAbsent(wpCode, k -> new ArrayList<>()).add(bot);
        }
      }

      for (final Map.Entry<String, List<AeroBotInternal>> entry : botsByWaypoint.entrySet()) {
        final List<AeroBotInternal> botsAtRack = entry.getValue();
        final List<AeroBotInternal> botsAtHighLevel = new ArrayList<>();

        for (final AeroBotInternal bot : botsAtRack) {
          if (bot.getCurrentLevel() > 0) {
            botsAtHighLevel.add(bot);
          }
        }

        if (botsAtHighLevel.size() > 1) {
          final Conflict conflict = new Conflict(tick, entry.getKey());
          for (final AeroBotInternal bot : botsAtHighLevel) {
            conflict.bots.add(bot.getCode());
            conflict.botLevels.put(bot.getCode(), bot.getCurrentLevel());
          }
          conflicts.add(conflict);
        }
      }
    }

    @Override
    public void generateHTML(final String filename) throws IOException {
      closeOpenIntervals();
      annotateMovementAreas();

      final File htmlFile = new File(filename);
      final String htmlName = htmlFile.getName();
      final int dot = htmlName.lastIndexOf('.');
      final String baseName = (dot <= 0) ? htmlName : htmlName.substring(0, dot);
      final String dataDirectoryName = baseName + "-data";

      final List<String> botCodes = new ArrayList<>(intervalsByBot.keySet());
      Collections.sort(botCodes);

      final ChunkBuildResult chunkData = buildOperationChunks(botCodes);
      final String botsJson = buildBotsJson(botCodes);
      final String conflictsJsonl = buildConflictsJsonl();
      final String manifestJson = buildManifestJson(chunkData.chunks);
      final String embeddedFilesJson = buildEmbeddedFilesJson(botsJson, conflictsJsonl, manifestJson,
          chunkData.chunkFiles);

      final String html = buildViewerHtml(embeddedFilesJson);
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(htmlFile))) {
        writer.write(html);
      }

      final File parent = htmlFile.getAbsoluteFile().getParentFile();
      final File standaloneHtmlFile = new File(parent, baseName + "-standalone.html");
      if (standaloneHtmlFile.exists() && !standaloneHtmlFile.delete()) {
        throw new IOException("Could not delete legacy standalone file: " + standaloneHtmlFile.getAbsolutePath());
      }

      deleteRecursivelyIfExists(new File(parent, dataDirectoryName));
    }

    private void closeOpenIntervals() {
      for (final Map.Entry<String, ActiveInterval> entry : activeIntervals.entrySet()) {
        final ActiveInterval active = entry.getValue();
        appendInterval(entry.getKey(), active.startTick, lastTick, active.state);
      }
      activeIntervals.clear();
    }

    private void appendInterval(final String botCode, final long startTick, final long endTick,
        final IntervalState state) {
      if (endTick < startTick) {
        return;
      }
      intervalsByBot.computeIfAbsent(botCode, k -> new ArrayList<>())
          .add(new IntervalRecord(startTick, endTick, state));
    }

    private static String areaCode(final String kind) {
      switch (kind) {
        case "charge":
          return "c";
        case "pick":
          return "p";
        case "park":
          return "k";
        case "load":
        case "store":
          return "a";
        default:
          return "r";
      }
    }

    private static String areaCode(final Waypoint waypoint) {
      if (waypoint == null) {
        return "r";
      }
      switch (waypoint.getType()) {
        case Charging:
          return "c";
        case Picking:
          return "p";
        case Parking:
          return "k";
        case Rack:
        default:
          return "r";
      }
    }

    private void annotateMovementAreas() {
      for (final List<IntervalRecord> records : intervalsByBot.values()) {
        for (int i = 0; i < records.size(); i++) {
          final IntervalRecord rec = records.get(i);
          if (!"move_h".equals(rec.state.kind)) {
            continue;
          }
          if (rec.state.fromArea == null) {
            rec.state.fromArea = "r";
            for (int j = i - 1; j >= 0; j--) {
              final String k = records.get(j).state.kind;
              if (!"wait".equals(k) && !"idle".equals(k)) {
                rec.state.fromArea = areaCode(k);
                break;
              }
            }
          }
          if (rec.state.toArea == null) {
            rec.state.toArea = "r";
            for (int j = i + 1; j < records.size(); j++) {
              final String k = records.get(j).state.kind;
              if (!"wait".equals(k) && !"idle".equals(k)) {
                rec.state.toArea = areaCode(k);
                break;
              }
            }
          }
        }
      }
    }

    private IntervalState buildState(final AeroBotInternal bot, final long tick) {
      final IntervalState state = new IntervalState();
      final Waypoint currentWaypoint = bot.getCurrentWaypoint();
      final String currentWaypointCode = (currentWaypoint != null) ? currentWaypoint.getCode() : "null";
      final int currentLevel = bot.getCurrentLevel();
      final OperationInternal operation = bot.currentOrJustFinishedOperation((int) tick);
      if (operation == null) {
        state.kind = (currentWaypoint != null && currentWaypoint.getType() == Waypoint.Type.Parking) ? "park" : "idle";
        state.source = currentWaypointCode;
        state.target = currentWaypointCode;
        state.fromLevel = currentLevel;
        state.toLevel = currentLevel;
        state.waiting = false;
        state.taskId = -1;
        return state;
      }

      state.waiting = operation.tickStart == 0;
      state.taskId = operation.taskId;
      state.source = currentWaypointCode;
      state.target = currentWaypointCode;
      state.fromLevel = currentLevel;
      state.toLevel = currentLevel;

      if (operation instanceof OperationInternal.AeroBotMoveH) {
        final OperationInternal.AeroBotMoveH op = (OperationInternal.AeroBotMoveH) operation;
        state.kind = "move_h";
        state.source = op.from.getCode();
        state.target = op.to.getCode();
        state.fromArea = areaCode(op.from);
        state.toArea = areaCode(op.to);
        state.fromLevel = 0;
        state.toLevel = 0;
      } else if (operation instanceof OperationInternal.AeroBotMoveV) {
        final OperationInternal.AeroBotMoveV op = (OperationInternal.AeroBotMoveV) operation;
        state.kind = op.to > op.from ? "move_up" : "move_down";
        state.source = currentWaypointCode;
        state.target = currentWaypointCode;
        state.fromLevel = op.from;
        state.toLevel = op.to;
      } else if (operation instanceof OperationInternal.AeroBotLoad) {
        final OperationInternal.AeroBotLoad op = (OperationInternal.AeroBotLoad) operation;
        state.kind = "load";
        state.target = op.container.getCode();
      } else if (operation instanceof OperationInternal.AeroBotStore) {
        state.kind = "store";
      } else if (operation instanceof OperationInternal.AeroBotPick) {
        final OperationInternal.AeroBotPick op = (OperationInternal.AeroBotPick) operation;
        state.kind = "pick";
        state.target = op.order.getProductCode();
      } else if (operation instanceof OperationInternal.AeroBotCharge) {
        state.kind = "charge";
      } else {
        state.kind = "idle";
      }

      if (state.waiting) {
        state.kind = "wait";
      }
      return state;
    }

    private String buildBotsJson(final List<String> botCodes) {
      final StringBuilder sb = new StringBuilder(Math.max(64, botCodes.size() * 12));
      sb.append('[');
      for (int i = 0; i < botCodes.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append(jsonString(botCodes.get(i)));
      }
      sb.append("]\n");
      return sb.toString();
    }

    private static class ChunkMeta {
      String fileName;
      long startTick = Long.MAX_VALUE;
      long endTick = Long.MIN_VALUE;
      int recordCount;
    }

    private static class ChunkBuildResult {
      List<ChunkMeta> chunks;
      Map<String, String> chunkFiles;
    }

    private ChunkBuildResult buildOperationChunks(final List<String> botCodes) {
      final Map<Integer, StringBuilder> chunkLines = new HashMap<>();
      final Map<Integer, ChunkMeta> metas = new TreeMap<>();
      for (final String botCode : botCodes) {
        final List<IntervalRecord> records = intervalsByBot.getOrDefault(botCode, Collections.emptyList());
        for (final IntervalRecord record : records) {
          long start = record.startTick;
          while (start <= record.endTick) {
            final int chunkIndex = (int) (start / DefaultVisualizer.CHUNK_SIZE_TICKS);
            final long chunkStartTick = ((long) chunkIndex) * DefaultVisualizer.CHUNK_SIZE_TICKS;
            final long chunkEndTick = chunkStartTick + DefaultVisualizer.CHUNK_SIZE_TICKS - 1;
            final long end = Math.min(record.endTick, chunkEndTick);

            StringBuilder lines = chunkLines.get(chunkIndex);
            ChunkMeta meta = metas.get(chunkIndex);
            if (lines == null) {
              lines = new StringBuilder(16 * 1024);
              chunkLines.put(chunkIndex, lines);

              meta = new ChunkMeta();
              meta.fileName = String.format("ops-%05d.jsonl", chunkIndex);
              metas.put(chunkIndex, meta);
            }

            lines.append(toJsonLine(botCode, start, end, record)).append('\n');

            meta.recordCount++;
            if (start < meta.startTick) {
              meta.startTick = start;
            }
            if (end > meta.endTick) {
              meta.endTick = end;
            }

            start = end + 1;
          }
        }
      }

      final Map<String, String> chunkFiles = new HashMap<>();
      for (final Map.Entry<Integer, ChunkMeta> entry : metas.entrySet()) {
        final ChunkMeta meta = entry.getValue();
        final StringBuilder lines = chunkLines.get(entry.getKey());
        chunkFiles.put(meta.fileName, (lines == null) ? "" : lines.toString());
      }

      final ChunkBuildResult result = new ChunkBuildResult();
      result.chunks = new ArrayList<>(metas.values());
      result.chunkFiles = chunkFiles;
      return result;
    }

    private String toJsonLine(final String botCode, final long start, final long end, final IntervalRecord record) {
      final StringBuilder sb = new StringBuilder(256);
      sb.append("{\"b\":").append(jsonString(botCode));
      sb.append(",\"s\":").append(start);
      sb.append(",\"e\":").append(end);
      sb.append(",\"os\":").append(record.startTick);
      sb.append(",\"oe\":").append(record.endTick);
      sb.append(",\"k\":").append(jsonString(record.state.kind));
      sb.append(",\"src\":").append(jsonString(record.state.source));
      sb.append(",\"tgt\":").append(jsonString(record.state.target));
      sb.append(",\"fl\":").append(record.state.fromLevel);
      sb.append(",\"tl\":").append(record.state.toLevel);
      sb.append(",\"w\":").append(record.state.waiting ? 1 : 0);
      if ("move_h".equals(record.state.kind)) {
        sb.append(",\"fa\":").append(jsonString(record.state.fromArea != null ? record.state.fromArea : "r"));
        sb.append(",\"ta\":").append(jsonString(record.state.toArea != null ? record.state.toArea : "r"));
      }
      sb.append("}");
      return sb.toString();
    }

    private String buildConflictsJsonl() {
      final StringBuilder out = new StringBuilder(Math.max(64, conflicts.size() * 64));
      for (final Conflict c : conflicts) {
        final StringBuilder sb = new StringBuilder(256);
        sb.append("{\"tick\":").append(c.tick);
        sb.append(",\"rack\":").append(jsonString(c.rackCode));
        sb.append(",\"bots\":[");
        for (int i = 0; i < c.bots.size(); i++) {
          if (i > 0) {
            sb.append(',');
          }
          sb.append(jsonString(c.bots.get(i)));
        }
        sb.append("]}");
        out.append(sb).append('\n');
      }
      return out.toString();
    }

    private String buildManifestJson(final List<ChunkMeta> chunks) {
      final StringBuilder writer = new StringBuilder(1024 + (chunks.size() * 72));
      writer.append("{\n");
      writer.append("  \"version\": 1,\n");
      writer.append("  \"chunkSize\": ").append(DefaultVisualizer.CHUNK_SIZE_TICKS).append(",\n");
      writer.append("  \"lastTick\": ").append(lastTick).append(",\n");
      writer.append("  \"botsFile\": \"bots.json\",\n");
      writer.append("  \"conflictsFile\": \"conflicts.jsonl\",\n");
      writer.append("  \"chunks\": [\n");
      for (int i = 0; i < chunks.size(); i++) {
        final ChunkMeta chunk = chunks.get(i);
        writer.append("    {\"file\": ").append(jsonString(chunk.fileName))
            .append(", \"start\": ").append(chunk.startTick)
            .append(", \"end\": ").append(chunk.endTick)
            .append(", \"count\": ").append(chunk.recordCount).append("}");
        if (i + 1 < chunks.size()) {
          writer.append(',');
        }
        writer.append('\n');
      }
      writer.append("  ]\n");
      writer.append("}\n");
      return writer.toString();
    }

    private String buildEmbeddedFilesJson(final String botsJson,
        final String conflictsJsonl, final String manifestJson, final Map<String, String> chunkFiles) {
      final StringBuilder sb = new StringBuilder(1024 * 64);
      sb.append('{');

      sb.append(jsonString("manifest.json")).append(':').append(jsonString(manifestJson));
      sb.append(',').append(jsonString("bots.json")).append(':').append(jsonString(botsJson));
      sb.append(',').append(jsonString("conflicts.jsonl")).append(':')
          .append(jsonString(conflictsJsonl));

      final List<String> files = new ArrayList<>(chunkFiles.keySet());
      Collections.sort(files);
      for (final String name : files) {
        sb.append(',').append(jsonString(name)).append(':').append(jsonString(chunkFiles.get(name)));
      }

      sb.append('}');
      return sb.toString();
    }

    private void deleteRecursivelyIfExists(final File target) {
      if ((target == null) || !target.exists()) {
        return;
      }
      if (target.isDirectory()) {
        final File[] children = target.listFiles();
        if (children != null) {
          for (final File child : children) {
            deleteRecursivelyIfExists(child);
          }
        }
      }
      target.delete();
    }

    private String buildViewerHtml(final String embeddedFilesJson) {
      String template = "" + //
          "<!DOCTYPE html>" + //
          "<html lang=\"en\">" + //
          "<head>" + //
          "  <meta charset=\"UTF-8\" />" + //
          "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />" + //
          "  <title>AeroBot Timeline</title>" + //
          "  <style>" + //
          "    :root {" + //
          "      --bg: #0c0c0c;" + //
          "      --row-bg: #0c0c0c;" + //
          "      --grid: #1e2333;" + //
          "      --text: #d6dbe9;" + //
          "      --muted: #8b93a8;" + //
          "      --bot-col: 72px;" + //
          "      --row-height: 32px;" + //
          "      --font-size: 14px;" + //
          "      --tick-px: 2px;" + //
          "    }" + //
          "    html, body {" + //
          "      margin: 0;" + //
          "      width: 100%;" + //
          "      height: 100%;" + //
          "      background: var(--bg);" + //
          "      color: var(--text);" + //
          "      overflow: hidden;" + //
          "      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, \"Liberation Mono\", \"Courier New\", monospace;"
          + //
          "    }" + //
          "    #viewport {" + //
          "      position: fixed;" + //
          "      inset: 0;" + //
          "      overflow-x: auto;" + //
          "      overflow-y: hidden;" + //
          "      background: var(--bg);" + //
          "    }" + //
          "    #canvas {" + //
          "      position: relative;" + //
          "      min-height: 100%;" + //
          "      padding-top: 25px;" + //
          "    }" + //
          "    #topbar {" + //
          "      position: sticky;" + //
          "      top: 0;" + //
          "      left: 0;" + //
          "      height: 24px;" + //
          "      z-index: 24;" + //
          "      display: flex;" + //
          "      background: rgba(15, 17, 26, 0.95);" + //
          "      border-bottom: 1px solid #2a3042;" + //
          "      box-sizing: border-box;" + //
          "    }" + //
          "    #ruler {" + //
          "      position: relative;" + //
          "      flex: 1 1 auto;" + //
          "      height: 24px;" + //
          "      pointer-events: none;" + //
          "    }" + //
          "    #corner-controls {" + //
          "      position: sticky;" + //
          "      top: 0;" + //
          "      left: 0;" + //
          "      width: var(--bot-col);" + //
          "      min-width: var(--bot-col);" + //
          "      max-width: var(--bot-col);" + //
          "      height: 24px;" + //
          "      display: flex;" + //
          "      align-items: center;" + //
          "      justify-content: center;" + //
          "      gap: 4px;" + //
          "      border-right: 1px solid #2a3042;" + //
          "      box-sizing: border-box;" + //
          "      background: rgba(15, 17, 26, 0.98);" + //
          "      z-index: 30;" + //
          "    }" + //
          "    #grid-overlay {" + //
          "      position: absolute;" + //
          "      left: var(--bot-col);" + //
          "      top: 25px;" + //
          "      z-index: 1;" + //
          "      pointer-events: none;" + //
          "      background-image: repeating-linear-gradient(" + //
          "        to right," + //
          "        rgba(130, 145, 185, 0.12) 0," + //
          "        rgba(130, 145, 185, 0.12) 1px," + //
          "        transparent 1px," + //
          "        transparent var(--tick-px)" + //
          "      );" + //
          "    }" + //
          "    .tick-label {" + //
          "      position: absolute;" + //
          "      top: 4px;" + //
          "      transform: translateX(-50%);" + //
          "      font-size: 11px;" + //
          "      color: #8fa3d1;" + //
          "      white-space: nowrap;" + //
          "    }" + //
          "    #corner-controls button {" + //
          "      width: 24px;" + //
          "      height: 20px;" + //
          "      border: 1px solid #3d4764;" + //
          "      background: #151b2c;" + //
          "      color: #d6dbe9;" + //
          "      cursor: pointer;" + //
          "      border-radius: 3px;" + //
          "      padding: 0;" + //
          "      line-height: 18px;" + //
          "      font-weight: 700;" + //
          "    }" + //
          "    #corner-controls button:hover {" + //
          "      background: #1d2640;" + //
          "    }" + //
          "    .row {" + //
          "      position: relative;" + //
          "      height: var(--row-height);" + //
          "      box-sizing: border-box;" + //
          "      display: flex;" + //
          "      z-index: 4;" + //
          "      border-bottom: 1px solid #111522;" + //
          "      background: var(--row-bg);" + //
          "      font-size: var(--font-size);" + //
          "      line-height: var(--row-height);" + //
          "    }" + //
          "    .bot {" + //
          "      position: sticky;" + //
          "      left: 0;" + //
          "      width: var(--bot-col);" + //
          "      min-width: var(--bot-col);" + //
          "      max-width: var(--bot-col);" + //
          "      z-index: 12;" + //
          "      box-sizing: border-box;" + //
          "      border-right: 1px solid var(--grid);" + //
          "      padding: 0 5px;" + //
          "      color: var(--muted);" + //
          "      background: #0c101b;" + //
          "      overflow: hidden;" + //
          "      text-overflow: ellipsis;" + //
          "      white-space: nowrap;" + //
          "    }" + //
          "    .track {" + //
          "      position: relative;" + //
          "      height: var(--row-height);" + //
          "    }" + //
          "    .seg {" + //
          "      position: absolute;" + //
          "      top: 2px;" + //
          "      height: calc(var(--row-height) - 4px);" + //
          "      border-radius: 2px;" + //
          "      padding: 0 4px;" + //
          "      box-sizing: border-box;" + //
          "      display: flex;" + //
          "      align-items: center;" + //
          "      overflow: hidden;" + //
          "      white-space: nowrap;" + //
          "      text-overflow: clip;" + //
          "      border: 1px solid rgba(255, 255, 255, 0.12);" + //
          "      cursor: default;" + //
          "      user-select: none;" + //
          "    }" + //
          "    .cont {" + //
          "      border-left: 2px dotted rgba(255, 255, 255, 0.95);" + //
          "    }" + //
          "    .load { background: #029aa9; color: #ffffff; }" + //
          "    .store { background: #029aa9; color: #ffffff; }" + //
          "    .pick { background: #5dae5d; color: #ffffff; }" + //
          "    .charge { background: #fcc633; color: #1a171b; }" + //
          "    .wait { background: #c50045; color: #ffffff; }" + //
          "    .idle { background: #1a171b; color: #cdced1; }" + //
          "    .park { background: #878889; color: #1a171b; }" + //
          "    .conflict { box-shadow: inset 0 0 0 2px #c50045, 0 0 0 1px #7a001f; }" + //
          "    #status {" + //
          "      position: fixed;" + //
          "      inset: 0;" + //
          "      display: none;" + //
          "      align-items: center;" + //
          "      justify-content: center;" + //
          "      background: rgba(0, 0, 0, 0.85);" + //
          "      z-index: 1000;" + //
          "      padding: 16px;" + //
          "      box-sizing: border-box;" + //
          "    }" + //
          "    #status .panel {" + //
          "      max-width: min(920px, 95vw);" + //
          "      border: 1px solid #31374a;" + //
          "      background: #111624;" + //
          "      color: #d6dbe9;" + //
          "      padding: 14px;" + //
          "      line-height: 1.4;" + //
          "      border-radius: 6px;" + //
          "      white-space: pre-wrap;" + //
          "      font-size: 12px;" + //
          "    }" + //
          "    #status.error .panel {" + //
          "      border-color: #7f1d1d;" + //
          "      background: #1a1010;" + //
          "    }" + //
          "  </style>" + //
          "</head>" + //
          "<body>" + //
          "  <div id=\"viewport\"><div id=\"canvas\"><div id=\"topbar\"><div id=\"corner-controls\"><button id=\"zoom-out\">−</button><button id=\"zoom-in\">+</button></div><div id=\"ruler\"></div></div><div id=\"grid-overlay\"></div></div></div>"
          + //
          "  <div id=\"status\"><div class=\"panel\"></div></div>" + //
          "  <script>" + //
          "    const EMBEDDED_FILES = __EMBEDDED_FILES_JSON__;" + //
          "    const AREA_COLORS = { r: '#006a74', a: '#029aa9', c: '#fcc633', p: '#5dae5d', k: '#878889' };" + //
          "    const isRackArea = k => k === 'r' || k === 'a';" + //
          "    const isSpecialArea = k => k === 'c' || k === 'p' || k === 'k';" + //
          "    const SPECIAL_SHARE = 30;" + //
          "    const NON_SPECIAL_SHARE = 70;" + //
          "    function areaColor(k) { return AREA_COLORS[k] ?? AREA_COLORS.r; }" + //
          "    function horizontalGradient(fromColor, toColor, sourceShare, blendHalf) {" + //
          "      if (fromColor === toColor) {" + //
          "        return fromColor;" + //
          "      }" + //
          "      const blendStart = Math.max(0, sourceShare - blendHalf);" + //
          "      const blendEnd = Math.min(100, sourceShare + blendHalf);" + //
          "      return `linear-gradient(to right, ${fromColor} 0%, ${fromColor} ${blendStart}%, ${toColor} ${blendEnd}%, ${toColor} 100%)`;"
          + //
          "    }" + //
          "    function weightedHorizontalGradient(fromArea, toArea) {" + //
          "      const fromColor = areaColor(fromArea);" + //
          "      const toColor = areaColor(toArea);" + //
          "      let sourceShare = 50;" + //
          "      if (isSpecialArea(toArea)) {" + //
          "        sourceShare = NON_SPECIAL_SHARE;" + //
          "      } else if (isSpecialArea(fromArea) && !isSpecialArea(toArea)) {" + //
          "        sourceShare = SPECIAL_SHARE;" + //
          "      }" + //
          "      return horizontalGradient(fromColor, toColor, sourceShare, 8);" + //
          "    }" + //
          "    function verticalMoveGradient(sourceColor, targetColor) {" + //
          "      return horizontalGradient(sourceColor, targetColor, 50, 35);" + //
          "    }" + //
          "    const BOT_COL = 72;" + //
          "    const ROW_HEIGHT = 32;" + //
          "    const BASE_PX_PER_TICK = Math.max(1.6, (window.innerWidth - BOT_COL) / 3000);" + //
          "    let pxPerTick = BASE_PX_PER_TICK * 3.1;" + //
          "" + //
          "    const viewport = document.getElementById('viewport');" + //
          "    const canvas = document.getElementById('canvas');" + //
          "    const topbar = document.getElementById('topbar');" + //
          "    const ruler = document.getElementById('ruler');" + //
          "    const gridOverlay = document.getElementById('grid-overlay');" + //
          "    const zoomOutButton = document.getElementById('zoom-out');" + //
          "    const zoomInButton = document.getElementById('zoom-in');" + //
          "" + //
          "    let manifest = null;" + //
          "    let bots = [];" + //
          "    let tracksByBot = new Map();" + //
          "    let segmentsByBot = new Map();" + //
          "    let loadedChunks = new Set();" + //
          "    let loadingChunks = new Set();" + //
          "    let conflictTicks = [];" + //
          "    let raf = null;" + //
          "" + //
          "    const statusNode = document.getElementById('status');" + //
          "    const statusPanel = statusNode.querySelector('.panel');" + //
          "" + //
          "    function showStatus(message, isError = false) {" + //
          "      statusPanel.textContent = message;" + //
          "      statusNode.className = isError ? 'error' : '';" + //
          "      statusNode.style.display = 'flex';" + //
          "    }" + //
          "" + //
          "    function hideStatus() {" + //
          "      statusNode.style.display = 'none';" + //
          "      statusNode.className = '';" + //
          "    }" + //
          "" + //
          "    function readEmbedded(path) {" + //
          "      const text = EMBEDDED_FILES[path];" + //
          "      if (typeof text !== 'string') {" + //
          "        throw new Error(`Missing embedded file: ${path}`);" + //
          "      }" + //
          "      return text;" + //
          "    }" + //
          "" + //
          "    function readEmbeddedJson(path) {" + //
          "      return JSON.parse(readEmbedded(path));" + //
          "    }" + //
          "" + //
          "    function emojiFor(seg) {" + //
          "      switch (seg.k) {" + //
          "        case 'move_h': return '➡️';" + //
          "        case 'move_up': return '⬆️';" + //
          "        case 'move_down': return '⬇️';" + //
          "        case 'load': return '📥';" + //
          "        case 'store': return '📤';" + //
          "        case 'pick': return '🎯';" + //
          "        case 'charge': return '⚡';" + //
          "        case 'wait': return '⏸️';" + //
          "        case 'park': return '🅿️';" + //
          "        default: return '💤';" + //
          "      }" + //
          "    }" + //
          "" + //
          "    function labelFor(seg) {" + //
          "      if (seg.k === 'move_h') {" + //
          "        return seg.tgt ?? '';" + //
          "      }" + //
          "      if (seg.k === 'move_up' || seg.k === 'move_down') {" + //
          "        return `L${seg.fl ?? 0}→L${seg.tl ?? 0}`;" + //
          "      }" + //
          "      if (seg.k === 'charge') {" + //
          "        return 'Charge';" + //
          "      }" + //
          "      if (seg.k === 'wait') {" + //
          "        return 'Wait';" + //
          "      }" + //
          "      if (seg.k === 'park') {" + //
          "        return 'Park';" + //
          "      }" + //
          "      return '';" + //
          "    }" + //
          "" + //
          "    function actionFor(seg) {" + //
          "      switch (seg.k) {" + //
          "        case 'move_h':" + //
          "          return 'Move';" + //
          "        case 'move_up':" + //
          "          return 'Move up';" + //
          "        case 'move_down':" + //
          "          return 'Move down';" + //
          "        case 'load':" + //
          "          return 'Load';" + //
          "        case 'store':" + //
          "          return 'Store';" + //
          "        case 'pick':" + //
          "          return 'Pick';" + //
          "        case 'charge':" + //
          "          return 'Charging';" + //
          "        case 'wait':" + //
          "          return 'Wait';" + //
          "        case 'park':" + //
          "          return 'Park';" + //
          "        default:" + //
          "          return 'Idle';" + //
          "      }" + //
          "    }" + //
          "" + //
          "    function tooltipFor(seg) {" + //
          "      const action = actionFor(seg);" + //
          "      const tickRange = `[${seg.os}..${seg.oe}]`;" + //
          "      if (seg.k === 'move_h') {" + //
          "        return `${action}: ${seg.src ?? ''} → ${seg.tgt ?? ''}\\n${tickRange}`;" + //
          "      }" + //
          "      if (seg.k === 'move_up' || seg.k === 'move_down') {" + //
          "        return `${action}: ${seg.src ?? ''}, L${seg.fl ?? 0} → L${seg.tl ?? 0}\\n${tickRange}`;" + //
          "      }" + //
          "      return `${action}: ${seg.src ?? ''}\\n${tickRange}`;" + //
          "    }" + //
          "" + //
          "    function niceStep(rawStep) {" + //
          "      const power = Math.pow(10, Math.floor(Math.log10(rawStep)));" + //
          "      const normalized = rawStep / power;" + //
          "      if (normalized <= 1) return 1 * power;" + //
          "      if (normalized <= 2) return 2 * power;" + //
          "      if (normalized <= 5) return 5 * power;" + //
          "      return 10 * power;" + //
          "    }" + //
          "" + //
          "    function gridLabelStep() {" + //
          "      const targetPx = 90;" + //
          "      const raw = Math.max(1, Math.ceil(targetPx / pxPerTick));" + //
          "      return niceStep(raw);" + //
          "    }" + //
          "" + //
          "    function applyZoom(anchorLeftTick = null) {" + //
          "      document.documentElement.style.setProperty('--tick-px', `${pxPerTick}px`);" + //
          "      createRows();" + //
          "      if (anchorLeftTick !== null && Number.isFinite(anchorLeftTick)) {" + //
          "        viewport.scrollLeft = Math.max(0, anchorLeftTick * pxPerTick);" + //
          "      }" + //
          "      scheduleRender();" + //
          "    }" + //
          "" + //
          "    function renderRuler(visibleStart, visibleEnd) {" + //
          "      ruler.style.width = `${Math.max(2, Math.ceil((manifest.lastTick + 2) * pxPerTick))}px`;" + //
          "      ruler.textContent = '';" + //
          "      const fragment = document.createDocumentFragment();" + //
          "      const step = gridLabelStep();" + //
          "      const first = Math.floor(visibleStart / step) * step;" + //
          "      for (let tick = first; tick <= visibleEnd + step; tick += step) {" + //
          "        if (tick < 0) {" + //
          "          continue;" + //
          "        }" + //
          "        const label = document.createElement('span');" + //
          "        label.className = 'tick-label';" + //
          "        label.style.left = `${tick * pxPerTick}px`;" + //
          "        label.textContent = String(tick);" + //
          "        fragment.appendChild(label);" + //
          "      }" + //
          "      ruler.appendChild(fragment);" + //
          "    }" + //
          "" + //
          "    function intersectsConflict(startTick, endTick) {" + //
          "      if (conflictTicks.length === 0) {" + //
          "        return false;" + //
          "      }" + //
          "      let lo = 0;" + //
          "      let hi = conflictTicks.length;" + //
          "      while (lo < hi) {" + //
          "        const mid = (lo + hi) >> 1;" + //
          "        if (conflictTicks[mid] < startTick) {" + //
          "          lo = mid + 1;" + //
          "        } else {" + //
          "          hi = mid;" + //
          "        }" + //
          "      }" + //
          "      return lo < conflictTicks.length && conflictTicks[lo] <= endTick;" + //
          "    }" + //
          "" + //
          "    function createRows() {" + //
          "      const totalWidth = Math.max(2, Math.ceil((manifest.lastTick + 2) * pxPerTick));" + //
          "      const totalHeight = bots.length * ROW_HEIGHT;" + //
          "      canvas.style.width = `${BOT_COL + totalWidth}px`;" + //
          "      canvas.style.height = `${Math.max(viewport.clientHeight, totalHeight)}px`;" + //
          "      viewport.style.overflowY = totalHeight > viewport.clientHeight ? 'auto' : 'hidden';" + //
          "      gridOverlay.style.width = `${totalWidth}px`;" + //
          "      gridOverlay.style.height = `${totalHeight}px`;" + //
          "" + //
          "      const fragment = document.createDocumentFragment();" + //
          "      for (const bot of bots) {" + //
          "        const row = document.createElement('div');" + //
          "        row.className = 'row';" + //
          "" + //
          "        const botCell = document.createElement('div');" + //
          "        botCell.className = 'bot';" + //
          "        botCell.textContent = bot;" + //
          "" + //
          "        const track = document.createElement('div');" + //
          "        track.className = 'track';" + //
          "        track.style.width = `${totalWidth}px`;" + //
          "" + //
          "        row.appendChild(botCell);" + //
          "        row.appendChild(track);" + //
          "        fragment.appendChild(row);" + //
          "        tracksByBot.set(bot, track);" + //
          "      }" + //
          "      canvas.replaceChildren(topbar, gridOverlay, fragment);" + //
          "    }" + //
          "" + //
          "    async function loadChunk(chunkIndex) {" + //
          "      if (loadedChunks.has(chunkIndex) || loadingChunks.has(chunkIndex)) {" + //
          "        return;" + //
          "      }" + //
          "      loadingChunks.add(chunkIndex);" + //
          "      const chunk = manifest.chunks[chunkIndex];" + //
          "      try {" + //
          "        const text = readEmbedded(chunk.file);" + //
          "        const lines = text.split('\\n');" + //
          "        for (const line of lines) {" + //
          "          if (!line) {" + //
          "            continue;" + //
          "          }" + //
          "          const seg = JSON.parse(line);" + //
          "          const list = segmentsByBot.get(seg.b);" + //
          "          if (list) {" + //
          "            list.push(seg);" + //
          "          }" + //
          "        }" + //
          "        for (const list of segmentsByBot.values()) {" + //
          "          list.sort((a, b) => a.s - b.s || a.e - b.e);" + //
          "        }" + //
          "        loadedChunks.add(chunkIndex);" + //
          "      } finally {" + //
          "        loadingChunks.delete(chunkIndex);" + //
          "      }" + //
          "      scheduleRender();" + //
          "    }" + //
          "" + //
          "    function ensureChunks(visibleStart, visibleEnd) {" + //
          "      for (let i = 0; i < manifest.chunks.length; i++) {" + //
          "        const c = manifest.chunks[i];" + //
          "        if (c.end < visibleStart || c.start > visibleEnd) {" + //
          "          continue;" + //
          "        }" + //
          "        loadChunk(i);" + //
          "      }" + //
          "    }" + //
          "" + //
          "    function renderRows() {" + //
          "      const visibleStart = Math.max(0, Math.floor(viewport.scrollLeft / pxPerTick));" + //
          "      const visibleEnd = Math.min(manifest.lastTick, Math.ceil((viewport.scrollLeft + viewport.clientWidth) / pxPerTick) + 2);"
          + //
          "" + //
          "      renderRuler(visibleStart, visibleEnd);" + //
          "" + //
          "      ensureChunks(visibleStart - manifest.chunkSize, visibleEnd + manifest.chunkSize);" + //
          "" + //
          "      for (const bot of bots) {" + //
          "        const track = tracksByBot.get(bot);" + //
          "        if (!track) {" + //
          "          continue;" + //
          "        }" + //
          "        const segs = segmentsByBot.get(bot) ?? [];" + //
          "        track.textContent = '';" + //
          "" + //
          "        for (const seg of segs) {" + //
          "          if (seg.e < visibleStart) {" + //
          "            continue;" + //
          "          }" + //
          "          if (seg.s > visibleEnd) {" + //
          "            break;" + //
          "          }" + //
          "          const drawStart = Math.max(seg.s, visibleStart);" + //
          "          const drawEnd = Math.min(seg.e, visibleEnd);" + //
          "          const left = Math.floor(drawStart * pxPerTick);" + //
          "          const width = Math.max(1, Math.ceil((drawEnd - drawStart + 1) * pxPerTick));" + //
          "" + //
          "          const el = document.createElement('div');" + //
          "          el.className = `seg ${seg.k}${seg.os < visibleStart ? ' cont' : ''}`;" + //
          "          if (intersectsConflict(drawStart, drawEnd)) {" + //
          "            el.className += ' conflict';" + //
          "          }" + //
          "          if (seg.k === 'move_h' || seg.k === 'move_up' || seg.k === 'move_down') {" + //
          "            let bg, txtColor;" + //
          "            if (seg.k === 'move_up') {" + //
          "              bg = verticalMoveGradient(AREA_COLORS.r, AREA_COLORS.a);" + //
          "              txtColor = '#ffffff';" + //
          "            } else if (seg.k === 'move_down') {" + //
          "              bg = verticalMoveGradient(AREA_COLORS.a, AREA_COLORS.r);" + //
          "              txtColor = '#ffffff';" + //
          "            } else {" + //
          "              const fa = seg.fa ?? 'r';" + //
          "              const ta = seg.ta ?? 'r';" + //
          "              if (isRackArea(fa) && isRackArea(ta)) {" + //
          "                bg = AREA_COLORS.r;" + //
          "                txtColor = '#ffffff';" + //
          "              } else {" + //
          "                bg = weightedHorizontalGradient(fa, ta);" + //
          "                txtColor = (ta === 'c') ? '#1a171b' : '#ffffff';" + //
          "              }" + //
          "            }" + //
          "            el.style.background = bg;" + //
          "            el.style.color = txtColor;" + //
          "          }" + //
          "          el.style.left = `${left}px`;" + //
          "          el.style.width = `${width}px`;" + //
          "          if (width < 42) {" + //
          "            el.style.padding = '0 1px';" + //
          "          }" + //
          "          el.textContent = `${emojiFor(seg)} ${labelFor(seg)}`.trim();" + //
          "          el.title = tooltipFor(seg);" + //
          "          track.appendChild(el);" + //
          "        }" + //
          "      }" + //
          "    }" + //
          "" + //
          "    function scheduleRender() {" + //
          "      if (raf) {" + //
          "        return;" + //
          "      }" + //
          "      raf = requestAnimationFrame(() => {" + //
          "        raf = null;" + //
          "        renderRows();" + //
          "      });" + //
          "    }" + //
          "" + //
          "    async function init() {" + //
          "      try {" + //
          "        manifest = readEmbeddedJson('manifest.json');" + //
          "        bots = readEmbeddedJson(manifest.botsFile);" + //
          "        const conflictText = readEmbedded(manifest.conflictsFile);" + //
          "        conflictTicks = conflictText" + //
          "          .split('\\n')" + //
          "          .filter(line => line.length > 0)" + //
          "          .map(line => JSON.parse(line).tick)" + //
          "          .filter(t => Number.isFinite(t))" + //
          "          .sort((a, b) => a - b);" + //
          "" + //
          "        for (const bot of bots) {" + //
          "          segmentsByBot.set(bot, []);" + //
          "        }" + //
          "        createRows();" + //
          "        hideStatus();" + //
          "        scheduleRender();" + //
          "      } catch (error) {" + //
          "        const message = (error && error.message) ? error.message : String(error);" + //
          "        showStatus(" + //
          "`Failed to load embedded visualization data." + //
          "" + //
          "Error:" + //
          "  ${message}" + //
          "" + //
          "Re-generate the visualization if this persists.`, true);" + //
          "      }" + //
          "    }" + //
          "" + //
          "    viewport.addEventListener('scroll', scheduleRender, { passive: true });" + //
          "    window.addEventListener('resize', () => {" + //
          "      createRows();" + //
          "      scheduleRender();" + //
          "    });" + //
          "    zoomInButton.addEventListener('click', () => {" + //
          "      const leftTick = viewport.scrollLeft / pxPerTick;" + //
          "      pxPerTick = Math.min(24, pxPerTick * 1.25);" + //
          "      applyZoom(leftTick);" + //
          "    });" + //
          "    zoomOutButton.addEventListener('click', () => {" + //
          "      const leftTick = viewport.scrollLeft / pxPerTick;" + //
          "      pxPerTick = Math.max(0.5, pxPerTick / 1.25);" + //
          "      applyZoom(leftTick);" + //
          "    });" + //
          "    init();" + //
          "  </script>" + //
          "</body>" + //
          "</html>";
      return template.replace("__EMBEDDED_FILES_JSON__", embeddedFilesJson);
    }

    private static String jsonString(final String value) {
      if (value == null) {
        return "\"\"";
      }
      final StringBuilder sb = new StringBuilder(value.length() + 8);
      sb.append('"');
      for (int i = 0; i < value.length(); i++) {
        final char c = value.charAt(i);
        switch (c) {
          case '"':
            sb.append("\\\"");
            break;
          case '\\':
            sb.append("\\\\");
            break;
          case '\n':
            sb.append("\\n");
            break;
          case '\r':
            sb.append("\\r");
            break;
          case '\t':
            sb.append("\\t");
            break;
          default:
            sb.append(c);
            break;
        }
      }
      sb.append('"');
      return sb.toString();
    }

    @Override
    public boolean hasConflicts() {
      return !conflicts.isEmpty();
    }

    @Override
    public long getFirstConflictTick() {
      return conflicts.isEmpty() ? -1 : conflicts.get(0).tick;
    }

    @Override
    public int getConflictCount() {
      return conflicts.size();
    }
  }

  // ----------------------------------------------------------------------------
}