package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class RidgeTests {

    @Test
    void recoversAnExactLinearRelationshipWhenBarelyPenalized() {
        double[][] x = {{1, 0}, {2, 1}, {3, 5}, {4, 2}, {5, 3}, {6, 8}};
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = 3 + 2 * x[i][0] - x[i][1];
        }
        Ridge ridge = Ridge.fit(x, y, 1e-12);
        assertThat(ridge.predict(new double[] {10, 4})).isCloseTo(3 + 20 - 4, within(1e-6));
    }

    @Test
    void shrinksOneStandardizedSlopeByOnePlusLambda() {
        // One column, x = 0..4 so mean 2 and population sd sqrt(2); y = 5x. The standardized
        // OLS slope is cov(z, y) = 5 sqrt(2); ridge divides it by (1 + λ).
        double[][] x = {{0}, {1}, {2}, {3}, {4}};
        double[] y = {0, 5, 10, 15, 20};
        double lambda = 1;
        Ridge ridge = Ridge.fit(x, y, lambda);
        double slope = 5 * Math.sqrt(2) / (1 + lambda);
        assertThat(ridge.standardizedCoefficients()[0]).isCloseTo(slope, within(1e-12));
        assertThat(ridge.predict(new double[] {4})).isCloseTo(10 + slope * 2 / Math.sqrt(2), within(1e-12));
    }

    @Test
    void aColumnThatNeverVariesGetsNoWeight() {
        double[][] x = {{1, 7}, {2, 7}, {3, 7}};
        double[] y = {2, 4, 6};
        Ridge ridge = Ridge.fit(x, y, 0.01);
        assertThat(ridge.standardizedCoefficients()[1]).isZero();
        assertThat(ridge.predict(new double[] {2, 1000})).isCloseTo(4, within(1e-9));
    }
}
