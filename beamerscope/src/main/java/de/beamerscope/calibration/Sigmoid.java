package de.beamerscope.calibration;

import org.apache.commons.math3.analysis.ParametricUnivariateFunction;

/**
 * Created by Bene on 15.12.16.
 */

class Sigmoid implements ParametricUnivariateFunction {
    public double value(double t, double... parameters) {
        final double a = parameters[0];
        final double b = parameters[1];
        final double c = parameters[2];
        final double d = parameters[3];

        return a + (b - c)/(1 + Math.pow(10., (c - t)*d));

//        return parameters[0] * Math.pow(t, parameters[1]) * Math.exp(-parameters[2] * t);
    }

    // Jacobian matrix of the above. In this case, this is just an array of
    // partial derivatives of the above function, with one element for each parameter.
    public double[] gradient(double t, double... parameters) {
        final double a = parameters[0];
        final double b = parameters[1];
        final double c = parameters[2];
        final double d = parameters[3];


        return new double[]{
                1, // d/da
                1/(Math.pow(10, d*(c-t))+1), //d/db
                (-d*Math.log(10)*(b-c)*Math.pow(10., d*(c-t)))/(Math.pow(Math.pow(10., d*(c-t)+1),2))-1/(Math.pow(10, d*(c-t))+1), //d/dc
                -(Math.log(10)*(b-c)*(c-t)*Math.pow(10., d*(c-t)))/(Math.pow(Math.pow(10., d*(c-t))+1, 2))}; //d/dd;

        /* return new double[] {
                Math.exp(-c*t) * Math.pow(t, b),
                a * Math.exp(-c*t) * Math.pow(t, b) * Math.log(t),
                a * (-Math.exp(-c*t)) * Math.pow(t, b+1)
        };*/
    }
}
