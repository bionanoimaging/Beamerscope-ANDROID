package de.beamerscope.utils;

import org.opencv.core.Mat;

public class complexMatrix{

        public Mat imagMat;
        public Mat realMat;
        public Mat phaseMat;
        public Mat magMat;

    public void release() {
        imagMat.release();
        realMat.release();
        phaseMat.release();
        magMat.release();
    }
}
