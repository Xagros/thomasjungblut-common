package de.jungblut.ner;

import de.jungblut.math.DoubleMatrix;
import de.jungblut.math.DoubleVector;
import de.jungblut.math.dense.DenseDoubleMatrix;
import de.jungblut.math.dense.DenseDoubleVector;
import de.jungblut.math.minimize.CostFunction;
import de.jungblut.math.minimize.DenseMatrixFolder;
import de.jungblut.math.tuple.Tuple;

/**
 * Conditional likelihood cost function, used in a hidden markov model to
 * optimize the weights.
 * 
 * @author thomas.jungblut
 * 
 */
public final class ConditionalLikelihoodCostFunction implements CostFunction {

  private static final double SIGMA_SQUARED = 10d * 10d;

  private final DoubleMatrix features;
  private final DoubleMatrix outcome;
  private final int m;
  private final int classes;

  public ConditionalLikelihoodCostFunction(DoubleMatrix features,
      DoubleMatrix outcome) {
    this.features = features;
    this.outcome = outcome;
    this.m = outcome.getRowCount();
    this.classes = outcome.getColumnCount() == 1 ? 2 : outcome.getColumnCount();
  }

  @Override
  public Tuple<Double, DoubleVector> evaluateCost(DoubleVector input) {
    // TODO if you are really caring about performance and memory usage, you can
    // implement some methods to translate the indices from matrices to vectors,
    // so you don't have to copy all that memory.
    DenseDoubleMatrix theta = DenseMatrixFolder.unfoldMatrix(input, classes,
        (int) (input.getLength() / (double) classes));
    DenseDoubleMatrix gradient = new DenseDoubleMatrix(theta.getRowCount(),
        theta.getColumnCount());

    double cost = 0d;
    // loop over all feature rows to determine the probabilities
    for (int row = 0; row < m; row++) {
      DoubleVector rowVector = features.getRowVector(row);
      double[] logProbabilities = new double[classes];
      // sum the probabilities for each class over all features
      for (int featureIndex = 0; featureIndex < rowVector.getLength(); featureIndex++) {
        for (int i = 0; i < classes; i++) {
          logProbabilities[i] += theta.get(i, featureIndex);
        }
      }
      double z = logSum(logProbabilities);
      for (int i = 0; i < classes; i++) {
        double prob = Math.exp(logProbabilities[i] - z);
        for (int featureIndex = 0; featureIndex < rowVector.getLength(); featureIndex++) {
          gradient.set(i, featureIndex, gradient.get(i, featureIndex) + prob);
          if (correctPrediction(i, outcome.getRowVector(row))) {
            gradient.set(i, featureIndex, gradient.get(i, featureIndex) - 1d);
          }
        }
        if (correctPrediction(i, outcome.getRowVector(row))) {
          cost -= Math.log(prob);
        }
      }
    }

    DenseDoubleVector foldGradient = DenseMatrixFolder.foldMatrix(gradient);

    // now add the prior and finalize the derivative
    cost += computeLogPrior(input, foldGradient);

    return new Tuple<Double, DoubleVector>(cost, foldGradient);
  }

  // checks if the prediction is correct, by comparing the index of the
  // predicted class to the maximum index of the outcome
  static boolean correctPrediction(int classIndex, DoubleVector outcome) {
    return outcome.maxIndex() == classIndex;
  }

  // compute the log prior fast by using one loop for both gradient and theta
  // instead of vectorizing (copy overhead)
  static double computeLogPrior(DoubleVector theta, DoubleVector gradient) {
    double prior = 0.0;
    for (int i = 0; i < theta.getLength(); i++) {
      prior += theta.get(i) * theta.get(i) / 2d / (SIGMA_SQUARED);
      gradient.set(i, gradient.get(i) + theta.get(i) / (SIGMA_SQUARED));
    }
    return prior;
  }

  // sum the logs and normalize them
  static double logSum(double[] logInputs) {
    int maxIdx = 0;
    double max = logInputs[0];
    for (int i = 1; i < logInputs.length; i++) {
      if (logInputs[i] > max) {
        maxIdx = i;
        max = logInputs[i];
      }
    }
    boolean haveTerms = false;
    double intermediate = 0.0;
    double cutoff = max - 30.0;
    // we avoid rearranging the array and so test indices each time!
    for (int i = 0; i < logInputs.length; i++) {
      if (i != maxIdx && logInputs[i] > cutoff) {
        haveTerms = true;
        intermediate += Math.exp(logInputs[i] - max);
      }
    }
    if (haveTerms) {
      return max + Math.log(1.0 + intermediate);
    } else {
      return max;
    }
  }

}