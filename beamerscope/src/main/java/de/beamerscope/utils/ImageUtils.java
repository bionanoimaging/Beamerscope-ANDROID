package de.beamerscope.utils;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static org.opencv.core.Core.mulSpectrums;

public class ImageUtils {

    public static final double PI =  3.141592653589793 ;
    private static final String TAG = "ImageUtils";


    public static Mat toLog(Mat Input){

            Mat magI = Input;
            Mat magI2 = new Mat(magI.size(), magI.type());
            Mat magI3 = new Mat(magI.size(), magI.type());
            Mat magI4 = new Mat(magI.size(), magI.type());
            Mat magI5 = new Mat(magI.size(), magI.type());

            //Core.add(magI, Mat.ones(paddedReal.rows(), paddedReal.cols(), CvType.CV_64FC1),  magI2); // switch to logarithmic scale
            Core.add(magI, Mat.ones(Input.rows(), Input.cols(), CvType.CV_32FC1),  magI2); // switch to logarithmic scale
            Core.log(magI2, magI3);

            Mat crop = new Mat(magI3, new Rect(0, 0, magI3.cols() & -2, magI3.rows() & -2));

            magI4 = crop.clone();

        return magI4;

    }

    public static Mat toMat(Bitmap bmp) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bmp, mat);
        return mat;
    }

    public static Bitmap toBitmap(Mat mat) {
        Bitmap bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmp);
        return bmp;
    }


    public static complexMatrix fresnelPropagator(complexMatrix complexInput, double z, double lambda, double pixelsize) {


        // This Method gives real/imag part as well as phase/magnitude of the result!
        // It needs real/imag-part as input!!

        complexMatrix complexOutput = new complexMatrix();

        //Make sure input Image is square!
        int rectSize=0;
        int colSize = complexInput.realMat.cols();
        int rowSize = complexInput.realMat.rows();

        float lambda_meter = (float) (lambda * 1e-9);  // Convert UserInput into Meter

        if(colSize>rowSize){
            rectSize=rowSize;
        }
        else {
            rectSize=colSize;
        }

        Rect roi = new Rect(0, 0, rectSize, rectSize);
        complexInput.realMat = new Mat(complexInput.realMat, roi);
        complexInput.imagMat = new Mat(complexInput.imagMat, roi);

        Log.i("TypebeforeColor", String.valueOf(complexInput.realMat));
        Log.i("TypebeforeColor", String.valueOf(complexInput.imagMat));

        //if(complexInput.realMat.type()!=Imgproc.COLOR_RGB2GRAY);
        complexInput.realMat.convertTo(complexInput.realMat, CvType.CV_32FC1);
        complexInput.imagMat.convertTo(complexInput.imagMat, CvType.CV_32FC1);

        int m = Core.getOptimalDFTSize(complexInput.realMat.rows());
        int n = Core.getOptimalDFTSize(complexInput.realMat.cols()); // on the border

        Log.i("DFT Size", String.valueOf(m)+"n"+String.valueOf(m));
        // add zero
        // values

        // Imgproc.copyMakeBorder(image1,
        // padded, 0, m -
        // image1.rows(), 0, n

        Mat paddedReal = new Mat(new Size(n, m), CvType.CV_32FC1); // expand input
        Mat paddedImag = new Mat(new Size(n, m), CvType.CV_32FC1); // expand input
        // image to
        // optimal size
        Core.copyMakeBorder(complexInput.realMat, paddedReal, 0, m - complexInput.realMat.rows(), 0, n - complexInput.realMat.cols(), Core.BORDER_CONSTANT);
        Core.copyMakeBorder(complexInput.imagMat, paddedImag, 0, m - complexInput.imagMat.rows(), 0, n - complexInput.imagMat.cols(), Core.BORDER_CONSTANT);



        //Create Spherical Wave Kernel
        Mat Kernel = Mat.zeros(paddedReal.rows(), paddedReal.cols(), CvType.CV_32FC2);
        complexMatrix HFresnel = fresnelKernel(z, lambda_meter, pixelsize, paddedReal.rows());
        //kernelSpherical(z, lambda_meter, Dataset.PIXELSIZE, padded.rows());



            List<Mat> planesKernel = new ArrayList<Mat>();
            planesKernel.add(HFresnel.imagMat);
            planesKernel.add(HFresnel.realMat);

            Core.merge(planesKernel, Kernel);   // Combine RE & IM Plane into a complex Matrix

        List<Mat> planes = new ArrayList<Mat>();
        planes.add(paddedReal);
        planes.add(paddedImag);

        Mat complexI = Mat.zeros(paddedReal.rows(), paddedReal.cols(), CvType.CV_32FC2);

        Core.merge(planes, complexI);       // Add to the expanded another plane with zeros
        //Ft = @(x) fftshift(fft2(x));
        Core.dft(complexI, complexI, Core.DFT_COMPLEX_OUTPUT, 0);//, Core.DFT_COMPLEX_OUTPUT, complexI.rows());      // this way the result may fit in the source matrix
        complexI = (fftshift(complexI));    // Shift the Spectrum to correct frequencys
        mulSpectrums(complexI, Kernel, complexI, 0);      // Multiply Kernel with Spectrum of Hologram
        //Ift = @(x) ifft2(ifftshift(x));             % Inverse transform and shift
        complexI = ifftshift(complexI);   // Shift back the Spectrum before backpropagation
        Core.idft(complexI, complexI,Core.DFT_COMPLEX_OUTPUT, 0);//, Core.DFT_COMPLEX_OUTPUT, complexI.rows());    // Propagate the Lightfield to appropriate distance

        // USE Ft and Ift
        /*
        % Function handles for Fourier transform and inverse fourier transform
        %F = @(x) ifftshift(fft2(fftshift(x)));     % from Lei
        %Ft = @(x) ifftshift(ifft2(fftshift(x)));   % from Lei

        % Defines a function called FT(), Ift()
        Ft = @(x) fftshift(fft2(x));                % Fourier transform and shift
        Ift = @(x) ifft2(ifftshift(x));             % Inverse transform and shift
        %http://stackoverflow.com/questions/20971945/fresnel-diffraction-in-two-steps
        %: U = ifft2(ifftshift(O.*H))
        */


         // compute the magnitude and switch to logarithmic scale
        // => log(1 + sqrt(Re(DFT(I))^2 + Im(DFT(I))^2))
        Core.split(complexI, planes); // planes[0] = Re(DFT(I), planes[1] = Im(DFT(I))

        complexOutput.realMat = planes.get(0);
        complexOutput.imagMat = planes.get(1);

        complexOutput.realMat.convertTo( complexOutput.realMat, CvType.CV_32FC1);
        complexOutput.imagMat.convertTo( complexOutput.imagMat, CvType.CV_32FC1);

        //complexOutput.phaseMat = new Mat(complexInput.realMat.size(),complexInput.realMat.type());
        //complexOutput.magMat = new Mat(complexInput.realMat.size(),complexInput.realMat.type());


        Log.i("Type", String.valueOf(complexOutput.magMat));
        Log.i("Type", String.valueOf(complexOutput.phaseMat));
        Log.i("Type", String.valueOf(complexOutput.realMat));
        Log.i("Type", String.valueOf(complexOutput.imagMat));

        //Core.cartToPolar(complexOutput.realMat, complexOutput.imagMat, complexOutput.magMat, complexOutput.phaseMat);

        complexOutput.phaseMat = new Mat(complexOutput.realMat.size(), CvType.CV_32FC1);
        complexOutput.magMat = new Mat(complexOutput.realMat.size(), CvType.CV_32FC1);
        //Core.magnitude(complexOutput.realMat, complexOutput.magMat, complexOutput.magMat);
        //Core.phase(complexOutput.realMat, complexOutput.magMat, complexOutput.phaseMat);

        return complexOutput;
    }



    private static Mat fftshift(Mat input){



        // rearrange the quadrants of Fourier image so that the origin is at the
        // image center
        int cx = input.cols() / 2;
        int cy = input.rows() / 2;

        Rect q0Rect = new Rect(0, 0, cx, cy);
        Rect q1Rect = new Rect(cx, 0, cx, cy);
        Rect q2Rect = new Rect(0, cy, cx, cy);
        Rect q3Rect = new Rect(cx, cy, cx, cy);

        Mat q0 = new Mat(input, q0Rect); // Top-Left - Create a ROI per quadrant
        Mat q1 = new Mat(input, q1Rect); // Top-Right
        Mat q2 = new Mat(input, q2Rect); // Bottom-Left
        Mat q3 = new Mat(input, q3Rect); // Bottom-Right

        Mat tmp = new Mat(); // swap quadrants (Top-Left with Bottom-Right)
        q0.copyTo(tmp);
        q3.copyTo(q0);
        tmp.copyTo(q3);

        q1.copyTo(tmp); // swap quadrant (Top-Right with Bottom-Left)
        q2.copyTo(q1);
        tmp.copyTo(q2);


        return input;

    }


    private static Mat ifftshift(Mat input){

        // rearrange the quadrants of Fourier image so that the origin is at the
        // image center
        int cx = input.cols() / 2;
        int cy = input.rows() / 2;

        Rect q0Rect = new Rect(0, 0, cx, cy);
        Rect q1Rect = new Rect(cx, 0, cx, cy);
        Rect q2Rect = new Rect(0, cy, cx, cy);
        Rect q3Rect = new Rect(cx, cy, cx, cy);

        Mat q1 = new Mat(input, q0Rect); // Top-Left - Create a ROI per quadrant
        Mat q2 = new Mat(input, q1Rect); // Top-Right
        Mat q3 = new Mat(input, q2Rect); // Bottom-Left
        Mat q4 = new Mat(input, q3Rect); // Bottom-Right

        Mat tmp = new Mat(); // swap quadrants (Top-Left with Bottom-Right)
        q1.copyTo(tmp);
        q3.copyTo(q1);
        tmp.copyTo(q3);

        q2.copyTo(tmp); // swap quadrant (Top-Right with Bottom-Left)
        q4.copyTo(q2);
        tmp.copyTo(q4);


        return input;

    }

    private static complexMatrix fresnelKernel(double z, double lambda, double pixelSize, int size){


        complexMatrix fresnel = new complexMatrix();
        fresnel.imagMat = new Mat(new Size(size, size), CvType.CV_32FC1);
        fresnel.realMat = new Mat(new Size(size, size), CvType.CV_32FC1);

        double grid_size = pixelSize * size;

        double Fx;
        double Fy;
        double phi;

        for(int i=1; i<size; i++){
            for(int j=1; j<size; j++){
                Fy = (i-(size-1)/2)*(1/grid_size);
                Fx = (j-(size-1)/2)*(1/grid_size);;
                phi=PI*lambda*z*(Fx*Fx+Fy*Fy)+(2*PI*z)/lambda;

                fresnel.imagMat.put(i, j, sin(phi));
                fresnel.realMat.put(i, j, cos(phi));

            }
        }

        return fresnel;

    }



    public static Mat ExtractAndCropMat(Mat inputMat, int subSize, int cchannel){
        // extracts a submatrix with size "subSize" and extracts the colour channel "cchannel"

        List<Mat> temp_bgr = new ArrayList<Mat>();

        int dpcWidth = inputMat.width();
        int dpcHeight = inputMat.height();

        inputMat = inputMat.submat((dpcHeight-subSize)/2, (dpcHeight+subSize)/2,(dpcWidth-subSize)/2, (dpcWidth+subSize)/2);
        Mat inputMat_sub = inputMat.clone();  // make sure that mat is continuous
        Core.split(inputMat_sub,temp_bgr); //split source
        return inputMat_sub = temp_bgr.get(cchannel); //green channel; red=2; blue=0
    }










}

