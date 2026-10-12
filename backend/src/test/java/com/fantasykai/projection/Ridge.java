package com.fantasykai.projection;

/**
 * Ridge regression on standardized columns, solved directly.
 *
 * <p>{@code (Z'Z/n + λI) β = Z'(y - ȳ)/n}, where Z is X standardized by the training rows'
 * own means and deviations. Dividing by n keeps λ comparable across positions with very
 * different row counts. The intercept is not penalized. A column that never varies in
 * training gets a zero coefficient rather than a division by zero.
 *
 * <p>No library: the systems are at most a few dozen columns square, and a Cholesky
 * factorization is twenty lines that a test can check against a closed form.
 */
final class Ridge {

    private final double[] mean;
    private final double[] scale;
    private final double[] beta;
    private final double intercept;

    private Ridge(double[] mean, double[] scale, double[] beta, double intercept) {
        this.mean = mean;
        this.scale = scale;
        this.beta = beta;
        this.intercept = intercept;
    }

    static Ridge fit(double[][] x, double[] y, double lambda) {
        int n = x.length;
        int d = n == 0 ? 0 : x[0].length;
        if (n == 0) {
            throw new IllegalArgumentException("no rows to fit");
        }
        double[] mean = new double[d];
        double[] scale = new double[d];
        double yMean = 0;
        for (int i = 0; i < n; i++) {
            yMean += y[i];
            for (int j = 0; j < d; j++) {
                mean[j] += x[i][j];
            }
        }
        yMean /= n;
        for (int j = 0; j < d; j++) {
            mean[j] /= n;
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                double dev = x[i][j] - mean[j];
                scale[j] += dev * dev;
            }
        }
        boolean[] constant = new boolean[d];
        for (int j = 0; j < d; j++) {
            scale[j] = Math.sqrt(scale[j] / n);
            constant[j] = scale[j] < 1e-12;
            if (constant[j]) {
                scale[j] = 1;
            }
        }

        double[][] a = new double[d][d];
        double[] b = new double[d];
        double[] z = new double[d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                z[j] = constant[j] ? 0 : (x[i][j] - mean[j]) / scale[j];
            }
            double yc = y[i] - yMean;
            for (int j = 0; j < d; j++) {
                b[j] += z[j] * yc;
                for (int k = 0; k <= j; k++) {
                    a[j][k] += z[j] * z[k];
                }
            }
        }
        for (int j = 0; j < d; j++) {
            b[j] /= n;
            for (int k = 0; k <= j; k++) {
                a[j][k] /= n;
                a[k][j] = a[j][k];
            }
            // A constant column has an all-zero row; λ alone keeps the system positive definite.
            a[j][j] += lambda > 0 ? lambda : 1e-12;
        }
        double[] beta = solve(a, b);
        for (int j = 0; j < d; j++) {
            if (constant[j]) {
                beta[j] = 0;
            }
        }
        return new Ridge(mean, scale, beta, yMean);
    }

    double predict(double[] x) {
        double value = intercept;
        for (int j = 0; j < beta.length; j++) {
            value += beta[j] * (x[j] - mean[j]) / scale[j];
        }
        return value;
    }

    /** Coefficients on the standardized scale, for the report's "what moved it". */
    double[] standardizedCoefficients() {
        return beta.clone();
    }

    /** Solves a symmetric positive-definite system by Cholesky factorization. */
    static double[] solve(double[][] a, double[] b) {
        int d = b.length;
        double[][] l = new double[d][d];
        for (int i = 0; i < d; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = a[i][j];
                for (int k = 0; k < j; k++) {
                    sum -= l[i][k] * l[j][k];
                }
                if (i == j) {
                    if (sum <= 0) {
                        throw new IllegalStateException("matrix is not positive definite at " + i);
                    }
                    l[i][i] = Math.sqrt(sum);
                } else {
                    l[i][j] = sum / l[j][j];
                }
            }
        }
        double[] y = new double[d];
        for (int i = 0; i < d; i++) {
            double sum = b[i];
            for (int k = 0; k < i; k++) {
                sum -= l[i][k] * y[k];
            }
            y[i] = sum / l[i][i];
        }
        double[] x = new double[d];
        for (int i = d - 1; i >= 0; i--) {
            double sum = y[i];
            for (int k = i + 1; k < d; k++) {
                sum -= l[k][i] * x[k];
            }
            x[i] = sum / l[i][i];
        }
        return x;
    }
}
