package de.beamerscope.calibration;

/**
 * Created by Bene on 15.12.16.
 */

public class Logit {

    public static double logit(double x, double... parameters){

        // calculates the inverse of the sigmoid function at a given x and parameter[] set
        final double a = parameters[0];
        final double b = parameters[1];
        final double c = parameters[2];
        final double d = parameters[3];

        double logit = c-(Math.log(-(b-x)/(a-x))/(d*Math.log(10)));

        return logit;

    }
}
