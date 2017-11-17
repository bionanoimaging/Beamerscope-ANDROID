//
// Created by Benedict Diederich on 25.08.16.
//


#include "common.h"

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>


#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <queue>
#include <sstream>
#include <string>

#include "opencv2/core.hpp"
#include "opencv2/core/utility.hpp"
#include "opencv2/imgproc.hpp"
#include "opencv2/imgcodecs.hpp"
#include "opencv2/highgui.hpp"

#include <iostream>
#include <ctime>
#include <sys/stat.h>
#include <string>
#include <stdio.h>


// own header files
#include "cvComplex.h"
#include "jni_illparameter.h"



#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)



using namespace cv;
using namespace std;





Mat getFFTVector(Mat img)
{   // Reads input Image (64x64), get FFT2 and reshapes it into a vector 1x8192 64Bit Double

    Mat complex_img = Mat();
    complex_create(img, img, complex_img);

    // Generate FT of image
    Mat img_Ft;
    fft2(complex_img, img_Ft);
    fftShift(img_Ft,img_Ft);


    // convert 2D FFT in 1D Vector
    // Format = |(REAL)(IMAG)|
    Mat img_Ft_vec_concat;
    Mat img_Ft_vec = img_Ft.reshape(0,1);

    Mat Ft_planes[2];
    split(img_Ft_vec,Ft_planes);
    hconcat(Ft_planes[0], Ft_planes[1], img_Ft_vec_concat);

    transpose(img_Ft_vec_concat, img_Ft_vec_concat);

    //log of spectrum and Zero-Center Data
    log(img_Ft_vec_concat, img_Ft_vec_concat);
    normalize(img_Ft_vec_concat, img_Ft_vec_concat, .5, -.5, NORM_MINMAX);

    if(false){
        LOGD("*********img_Ft**********");
        printMat(img_Ft);

        LOGD("*********img_Ft_vec**********");
        printMat(img_Ft_vec);

        LOGD("*********img_Ft_vec_concat**********");
        printMat(img_Ft_vec_concat);

        LOGD("*********img_Ft_vec_concat**********");
        printMat(img_Ft_vec_concat);


	for(int i= 0; i<8192; ++i) {
		double value = (double) img_Ft_vec_concat.at<double>(i, 0);
		LOGD("input:  %f", value);
	}

    }

    return img_Ft_vec_concat;
}



Mat getAngleVector(Mat cmplx_angle)
{   // Reads input Image (64x64), get FFT2 and reshapes it into a vector 1x8192 64Bit Double

    // rehape image to 1D-Vector
    Mat cmplx_vec = cmplx_angle.reshape(1,1);

    LOGD("*********cmplx_vec**********");
    printMat(cmplx_vec);
    // gives: sz_cols:  4096, sz_rows:  1, Thus it needs to be transposed
    transpose(cmplx_vec, cmplx_vec);
    // gives: sz_cols:  1, sz_rows:  4096

    // this step is done temporarly, due to wrong pixelvalues
    normalize(cmplx_vec, cmplx_vec, 0, 2*3.14, NORM_MINMAX);


    /*
	for(int i= 0; i<64*64; ++i) {
		double value = (double) cmplx_vec.at<double>(0, i);
		LOGD("input:  %f", value);
	}
    */


    return cmplx_vec;
}
