# Road grade model

Reviewed: 2026-11-27

The offline graph stores a forward and backward grade for each directed road edge.

1. Valid OSM `incline`, `incline:forward`, or `incline:backward` tags take precedence. An explicit `0%` is treated as an intentional value and is not replaced by DEM data.
2. If no valid OSM incline tag exists, the graph builder samples the local Copernicus GLO-90 elevation tile at the edge endpoints.
3. Each endpoint elevation is the median of a 3×3 pixel window to reduce isolated pixel noise.
4. Edges shorter than 25 m are not assigned a derived grade. Absolute grades below 2% are treated as flat; derived grades are clamped to ±30%.
5. The forward grade is `(end elevation - start elevation) / edge length × 100`; the backward grade has the opposite sign.
6. If a DEM tile is missing or all nearby pixels are nodata, no grade is invented and the edge retains its OSM/default value.

The router applies profile-specific directional slope costs. A positive grade is uphill in the edge's stored direction; a negative grade is downhill.

## Limitations

- GLO-90 is a 90 m elevation product; short, sharp ramps and cuttings may not be represented accurately.
- The stored grade is an average over the graph edge. A long edge containing both a climb and descent can have a modest net grade.
- DEM-derived grades are a fallback, not a replacement for surveyed road gradients or reliable OSM incline tags.
- Via-way and conditional OSM turn restrictions remain separate routing work.
