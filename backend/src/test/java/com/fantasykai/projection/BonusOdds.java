package com.fantasykai.projection;

import com.fantasykai.scoring.StatKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P(stat ≥ threshold | projection), measured rather than assumed.
 *
 * <p>For each position and stat, the training rows' projections are split into equal-count
 * bins, and each bin keeps the actual results it saw. The probability for a new projection
 * is the share of its bin's actual results that reached the threshold -- no distribution
 * is assumed, so a lumpy, zero-heavy stat like rushing yards gets its own shape.
 *
 * <p>The bins are fitted on in-sample training projections, which flatters them slightly; the
 * test season's Brier score and reliability table are what say whether that matters.
 */
final class BonusOdds implements ExpectedPoints.Odds {

    static final int BINS = 10;

    private record Bins(double[] upper, double[][] actuals) {}

    private final Map<String, Bins> bins;

    private BonusOdds(Map<String, Bins> bins) {
        this.bins = bins;
    }

    interface Projector {
        double[] project(Case c);
    }

    static BonusOdds fit(List<Case> train, Projector model) {
        Map<String, List<double[]>> pairs = new HashMap<>();
        for (Case c : train) {
            double[] projected = model.project(c);
            double[] actual = c.actualLine();
            for (StatKey stat : StatKey.values()) {
                pairs.computeIfAbsent(c.position + ":" + stat.json(), k -> new ArrayList<>())
                        .add(new double[] {projected[stat.index()], actual[stat.index()]});
            }
        }
        Map<String, Bins> bins = new HashMap<>();
        pairs.forEach((key, list) -> {
            if (list.size() < BINS * 10) {
                return;
            }
            list.sort(Comparator.comparingDouble((double[] p) -> p[0]).thenComparingDouble(p -> p[1]));
            double[] upper = new double[BINS];
            double[][] actuals = new double[BINS][];
            for (int b = 0; b < BINS; b++) {
                int from = b * list.size() / BINS;
                int to = (b + 1) * list.size() / BINS;
                actuals[b] = new double[to - from];
                for (int i = from; i < to; i++) {
                    actuals[b][i - from] = list.get(i)[1];
                }
                Arrays.sort(actuals[b]);
                upper[b] = b == BINS - 1 ? Double.POSITIVE_INFINITY : list.get(to - 1)[0];
            }
            bins.put(key, new Bins(upper, actuals));
        });
        return new BonusOdds(bins);
    }

    /**
     * The probability a {@code position} player projected for {@code projected} reaches
     * {@code gte}. A pair with too little training data falls back to the step -- reached or
     * not -- and {@link #measured} says which pairs that is.
     */
    @Override
    public double probability(String position, StatKey stat, double gte, double projected) {
        Bins b = bins.get(position + ":" + stat.json());
        if (b == null) {
            return projected >= gte ? 1 : 0;
        }
        int bin = 0;
        while (projected > b.upper()[bin]) {
            bin++;
        }
        double[] actuals = b.actuals()[bin];
        int lo = 0;
        int hi = actuals.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (actuals[mid] < gte) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return (double) (actuals.length - lo) / actuals.length;
    }

    boolean measured(String position, StatKey stat) {
        return bins.containsKey(position + ":" + stat.json());
    }
}
