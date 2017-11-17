//cvComplex.h

#include <time.h>
//#include <opencv2/contrib.hpp>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
#include <iostream>
#include <string>
#include <stdio.h>
#include <fstream>
#include <vector>

#if !defined(CVCOMPLEX_CONSTANTS_H)
#define CVCOMPLEX_CONSTANTS_H 1

#ifdef __cplusplus
extern "C" {
#endif

/*
static const int SHOW_COMPLEX_MAG = 0;
static const int SHOW_COMPLEX_COMPONENTS = 1;
static const int SHOW_COMPLEX_REAL = 2;
static const int SHOW_COMPLEX_IMAGINARY = 3;
*/


//std::complex<double> ii = std::complex<double>(0, 1);
void circularShift(cv::Mat img, cv::Mat result, int x, int y);
void maxComplexReal(cv::Mat& m, std::string label);
void complexConj(const cv::Mat& m, cv::Mat& output);
void complexAbs(const cv::Mat& m, cv::Mat& output);
void complexMultiply(const cv::Mat& m1, const cv::Mat& m2, cv::Mat& output);
void complexScalarMultiply(double scaler, cv::Mat& m, cv::Mat output);
void complexDivide(const cv::Mat& m1, const cv::Mat& m2, cv::Mat& output);
void complexInverse(const cv::Mat& m1, const cv::Mat& m2, cv::Mat& output);
void fftShift(const cv::Mat& input, cv::Mat& output);
void ifftShift(const cv::Mat& input, cv::Mat& output);
void fft2(cv::Mat& input, cv::Mat& output);
void ifft2(cv::Mat& input, cv::Mat& output);
void complex_imwrite(std::string fname, cv::Mat& m1);
void showImgMag(cv::Mat m, std::string windowTitle);
void showImg(cv::Mat m, std::string windowTitle);
void showImgObject(cv::Mat m, std::string windowTitle);
void showImgFourier(cv::Mat m, std::string windowTitle);
void showComplexImg(cv::Mat m, int displayFlag, std::string windowTitle);
void getMagnitude(cv::Mat& input, cv::Mat& output);
double realMean(cv::Mat& input);
double imagMean(cv::Mat& input);
void fresnelProp(cv::Mat& input, cv::Mat& output, double zDist, double lambda, double ps_eff);
void genFresnelKernel(const cv::Mat& input, cv::Mat& kernelMat, double zDist, double lambda, double du, double dv);
void complex_create(cv::Mat&  mat_real, cv::Mat&  mat_imag, cv::Mat& output);
cv::Mat GetSquareImage( const cv::Mat& img, int target_width = 500 );
void printMat(cv::Mat m);
#ifdef __cplusplus
}
#endif


#endif

