/*
 * cvComplex.cpp
 * A set of functions for dealing with two channel complex matricies inside
 * the OpenCV 2.4.* Imaging processing library. Complex numbers are generally
 * dealt with using a 2 color channel Mat object rather than std::complex.
 * This library provides several common functions for manipulating matricies
 * in this format.
 *
 * Maintained by Z. Phillips, Computational Imaging Lab, UC Berkeley
 * Report bugs directly to zkphil@berkeley.edu
 */

//#include "opencv2/contrib.hpp"
#include "opencv2/core.hpp"
#include "opencv2/imgproc.hpp"
#include "opencv2/highgui.hpp"
#include <iostream>
#include <string>
#include <stdio.h>
//#include <dirent.h>
#include <fstream>
#include <vector>
#include "cvComplex.h"

#include <string>
#include <iostream>
#include <fstream>
#include <sstream>
#include <cstdio>

#include <jni.h>
#include <android/log.h>

#include "common.h"




static const int SHOW_COMPLEX_MAG = 0;
static const int SHOW_COMPLEX_COMPONENTS = 1;
static const int SHOW_COMPLEX_REAL = 2;
static const int SHOW_COMPLEX_IMAGINARY = 3;

static const int CMAP_MIN = 0;
static const int CMAP_MAX = 11;
static const int COLORMAP_NONE = -1;

int gv_cMap = -1; // Global Colormap Setting
//std::complex<double> ii = std::complex<double>(0, 1);

using namespace cv;


extern "C" {


void circularShift(cv::Mat img, cv::Mat result, int x, int y){
    int w = img.cols;
    int h = img.rows;

    int shiftR = x % w;
    int shiftD = y % h;

    if (shiftR < 0)//if want to shift in -x direction
        shiftR += w;

    if (shiftD < 0)//if want to shift in -y direction
        shiftD += h;

    cv::Rect gate1(0, 0, w-shiftR, h-shiftD);//rect(x, y, width, height)
    cv::Rect out1(shiftR, shiftD, w-shiftR, h-shiftD);

    cv::Rect gate2(w-shiftR, 0, shiftR, h-shiftD);
    cv::Rect out2(0, shiftD, shiftR, h-shiftD);

    cv::Rect gate3(0, h-shiftD, w-shiftR, shiftD);
    cv::Rect out3(shiftR, 0, w-shiftR, shiftD);

    cv::Rect gate4(w-shiftR, h-shiftD, shiftR, shiftD);
    cv::Rect out4(0, 0, shiftR, shiftD);

    cv::Mat shift1 = img ( gate1 );
	 cv::Mat shift2 = img ( gate2 );
	 cv::Mat shift3 = img ( gate3 );
	 cv::Mat shift4 = img ( gate4 );
	 shift1.copyTo(cv::Mat(result, out1));
	 shift2.copyTo(cv::Mat(result, out2));
	 shift3.copyTo(cv::Mat(result, out3));
	 shift4.copyTo(cv::Mat(result, out4));
}



double fastSqrt(double x) {
    if (x <= 0)
        return 0;       // if negative number throw an exception?
    int exp = 0;
    x = frexp(x, &exp); // extract binary exponent from x
    if (exp & 1) {      // we want exponent to be even
        exp--;
        x *= 2;
    }
    double y = (1+x)/2; // first approximation
    double z = 0;
    while (y != z) {    // yes, we CAN compare doubles here!
        z = y;
        y = (y + x/y) / 2;
    }
    return ldexp(y, exp/2); // multiply answer by 2^(exp/2)
}

void maxComplexReal(cv::Mat& m, std::string label)
{
      cv::Mat planes[] = {cv::Mat::zeros(m.rows, m.cols, m.type()), cv::Mat::zeros(m.rows, m.cols, m.type())};
      cv::split(m,planes);
      double minVal, maxVal;
      cv::minMaxLoc(planes[0], &minVal, &maxVal);
      std::cout << "Max/Min values of " <<label << " are: " << maxVal << ", " << minVal << std::endl;
}

void complexConj(const cv::Mat& m, cv::Mat& output)
{
   if (output.empty())
   	output = cv::Mat::zeros(m.rows, m.cols, CV_64FC2);

	for(int i = 0; i < m.rows; i++) // loop through y
	{
    const double* m_i = m.ptr<double>(i);  // Input
    double* o_i = output.ptr<double>(i);   // Output

    for(int j = 0; j < m.cols; j++)
    {
        o_i[j*2] = (double) m_i[j*2];
        o_i[j*2+1] = (double) -1.0 * m_i[j*2+1];
    }
	}
}

void complexAbs(const cv::Mat& m, cv::Mat& output)
{

   if (output.empty())
   	output = cv::Mat::zeros(m.rows, m.cols, CV_64FC2);

	for(int i = 0; i < m.rows; i++) // loop through y
	{
    const double* m_i = m.ptr<double>(i);  // Input
    double* o_i = output.ptr<double>(i);   // Output

    for(int j = 0; j < m.cols; j++)
    {

        o_i[j*2] = (double) std::sqrt(m_i[j*2] * m_i[j*2] + m_i[j*2+1] * m_i[j*2+1]);
        o_i[j*2+1] = 0.0;
    }
	}
}

/* complexMultiply(const cv::Mat& m1, const cv::Mat& m2, cv::Mat& output)
 * Multiplies 2 complex matricies where the first two color channels are the
 * real and imaginary coefficents, respectivly. Uses the equation:
 *         (a + bi) * (c + di) = (ac - bd) + (ad + bc)i
 *
 * INPUTS:
 *   const cv::Mat& m1:  Complex Matrix 1
 *   const cv::Mat& m2:  Complex Matrix 2
 * OUTPUT:
 *   cv::Mat& outpit:    Complex Product of m1 and m2
 */
void complexMultiply(const cv::Mat& m1, const cv::Mat& m2, cv::Mat& output)
{
   if (output.empty())
   	output = cv::Mat::zeros(m1.rows, m1.cols, CV_64FC2);
   // (a + bi) * (c + di) = (ac - bd) + (ad + bc)i
	for(int i = 0; i < m1.rows; i++) // loop through y
	{
    const double* m1_i = m1.ptr<double>(i);   // Input 1
    const double* m2_i = m2.ptr<double>(i);   // Input 2
    double* o_i = output.ptr<double>(i);      // Output
    for(int j = 0; j < m1.cols; j++)
    {
        o_i[j*2] = (m1_i[j*2] * m2_i[j*2]) - (m1_i[j*2+1] * m2_i[j*2+1]);    // Real
        o_i[j*2+1] = (m1_i[j*2] * m2_i[j*2+1]) + (m1_i[j*2+1] * m2_i[j*2]);  // Imaginary
    }
	}
}

void complexScalarMultiply(double scaler, cv::Mat& m, cv::Mat output)
{
   if (output.empty())
   	output = cv::Mat::zeros(m.rows, m.cols, CV_64FC2);
   // (a + bi) * (c + di) = (ac - bd) + (ad + bc)i
	for(int i = 0; i < m.rows; i++) // loop through y
	{
    const double* m_i = m.ptr<double>(i);   // Input 1
    double* o_i = output.ptr<double>(i);      // Output
    for(int j = 0; j < m.cols; j++)
    {
        o_i[j*2] = scaler * m_i[j*2]; // Real
        o_i[j*2+1] = scaler * m_i[j*2+1]; // Real
    }
	}
}

void complexDivide(const cv::Mat& m1, const cv::Mat& m2, cv::Mat& output)
{
   if (output.empty())
   	output = cv::Mat::zeros(m1.rows, m1.cols, CV_64FC2);
   // (a+bi) / (c+di) = (ac+bd) / (c^2+d^2) + (bc-ad) / (c^2+d^2) * i
	for(int i = 0; i < m1.rows; i++) // loop through y
	{
    const double* m1_i = m1.ptr<double>(i);   // Input 1
    const double* m2_i = m2.ptr<double>(i);   // Input 2
    double* o_i = output.ptr<double>(i);      // Output
    for(int j = 0; j < m1.cols; j++)
    {
        o_i[j*2] = ((m1_i[j*2] * m2_i[j*2]) + (m1_i[j*2+1] * m2_i[j*2+1])) / (m2_i[j*2] * m2_i[j*2] + m2_i[j*2+1] * m2_i[j*2+1]); // Real
        o_i[j*2+1] = ((m1_i[j*2+1] * m2_i[j*2]) - (m1_i[j*2] * m2_i[j*2+1])) / (m2_i[j*2] * m2_i[j*2] + m2_i[j*2+1] * m2_i[j*2+1]);  // Imaginary
    }
	}
}

void complexInverse(const cv::Mat& m1, const cv::Mat& m2, cv::Mat& output)
{
   if (output.empty())
   	output = cv::Mat::zeros(m1.rows, m1.cols, CV_64FC2);
   // (a+bi) / (c+di) = (ac+bd) / (c^2+d^2) + (bc-ad) / (c^2+d^2) * i
	for(int i = 0; i < m1.rows; i++) // loop through y
	{
    const double* m1_i = m1.ptr<double>(i);   // Input 1
    const double* m2_i = m2.ptr<double>(i);   // Input 2
    double* o_i = output.ptr<double>(i);      // Output
    for(int j = 0; j < m1.cols; j++)
    {
        o_i[j*2] = ((m2_i[j*2]) + ( m2_i[j*2+1])) / (m2_i[j*2] * m2_i[j*2] + m2_i[j*2+1] * m2_i[j*2+1]); // Real
        o_i[j*2+1] = (( m2_i[j*2]) - (m2_i[j*2+1])) / (m2_i[j*2] * m2_i[j*2] + m2_i[j*2+1] * m2_i[j*2+1]);  // Imaginary
    }
	}
}

// Depreciated fftshift methods

/*
cv::Mat fftShift(cv::Mat m)
{
      cv::Mat shifted = cv::Mat(m.cols,m.rows,m.type());
      circularShift(m, shifted, std::ceil((double) m.cols/2), std::ceil((double) m.rows/2));
      return shifted;
}

cv::Mat ifftShift(cv::Mat m)
{
      cv::Mat shifted = cv::Mat(m.cols,m.rows,m.type());
      circularShift(m, shifted, std::floor((double) m.cols/2), std::floor((double)m.rows/2));
      return shifted;
}
*/ //end depreciation

void fftShift(const cv::Mat& input, cv::Mat& output)
{
	if ((input.data == output.data) || output.empty())
	{
		cv::Mat shifted = cv::Mat::zeros(input.cols, input.rows, input.type());
		circularShift(input, shifted, std::ceil((double) input.cols/2), std::ceil((double) input.rows/2));
		output = shifted.clone();
	}
	else
	 	circularShift(input, output, std::ceil((double) input.cols/2), std::ceil((double) input.rows/2));
}

void ifftShift(const cv::Mat& input, cv::Mat& output)
{
      if ((input.data == output.data) || output.empty())
      {
      	cv::Mat shifted = cv::Mat::zeros(input.cols, input.rows, input.type());
         circularShift(input, shifted, std::floor((double) input.cols/2), std::floor((double) input.rows/2));
         output = shifted.clone();
      }
      else
       	circularShift(input, output, std::floor((double) input.cols/2), std::floor((double) input.rows/2));
}
/*
void ifftShift2(const cv::Mat& input, cv::Mat& output)
{
	circularShift(input, output, std::floor((double) input.cols/2), std::floor((double) input.rows/2));
	showComplexImg(output,-1,"ifftshift2 Result");
}
*/
// Opencv fft implimentation
void fft2(cv::Mat& input, cv::Mat& output)
{
   cv::Mat paddedInput;
   int m = cv::getOptimalDFTSize( input.rows );
   int n = cv::getOptimalDFTSize( input.cols );

   // Zero pad for Speed
   cv::copyMakeBorder(input, paddedInput, 0, m - input.rows, 0, n - input.cols, cv::BORDER_CONSTANT, cv::Scalar::all(0));
   cv::dft(paddedInput, output, cv::DFT_COMPLEX_OUTPUT);
}

// Opencv ifft implimentation
void ifft2(cv::Mat& input, cv::Mat& output)
{
   cv::Mat paddedInput;
   int m = cv::getOptimalDFTSize( input.rows );
   int n = cv::getOptimalDFTSize( input.cols );

   // Zero pad for speed
   cv::copyMakeBorder(input, paddedInput, 0, m - input.rows, 0, n - input.cols, cv::BORDER_CONSTANT, cv::Scalar::all(0));
   cv::dft(paddedInput, output, cv::DFT_INVERSE | cv::DFT_COMPLEX_OUTPUT | cv::DFT_SCALE); // Real-space of object
}

void complex_imwrite(std::string fname, cv::Mat& m1)
{
   cv::Mat outputPlanes[] = {cv::Mat::zeros(m1.rows, m1.cols, m1.type()), cv::Mat::zeros(m1.rows, m1.cols, m1.type()),cv::Mat::zeros(m1.rows, m1.cols, m1.type())};
   cv::Mat inputPlanes[] = {cv::Mat::zeros(m1.rows, m1.cols, m1.type()), cv::Mat::zeros(m1.rows, m1.cols, m1.type())};

   cv::split(m1,inputPlanes);
   outputPlanes[0] = inputPlanes[0];
   outputPlanes[1] = inputPlanes[1];
   cv::Mat outMat;
   cv::merge(outputPlanes,3,outMat);
   cv::imwrite(fname,outMat);
}





void showComplexImg(cv::Mat m, int displayFlag, std::string windowTitle)
{
	std::cout << "Showing Image" << std::endl;
   if (m.channels() == 2) // Ensure Complex Matrix
   {
		cv::Mat planes[] = {cv::Mat::zeros(m.rows, m.cols, m.type()), cv::Mat::zeros(m.rows, m.cols, m.type())};
		cv::split(m, planes);                   // planes[0] = Re(DFT(I), planes[1] = Im(DFT(I))

		switch(displayFlag)
		{
			case (SHOW_COMPLEX_MAG):
			{
			cv::magnitude(planes[0], planes[1], planes[0]);// planes[0] = magnitude
			std::string magWindowTitle = windowTitle + " Magnitude";
			pow(planes[0], 2.0, planes[0]);
			showImg(planes[0], magWindowTitle);
				break;
			}
			case (SHOW_COMPLEX_REAL):
			{
			   std::string reWindowTitle = windowTitle + " Real";
				 showImg(planes[0], reWindowTitle);
				break;
			}
			case (SHOW_COMPLEX_IMAGINARY):
			{
				std::string imWindowTitle = windowTitle + " Imaginary";
            showImg(planes[1], imWindowTitle);
				break;
			}
			default:
			{

				std::string reWindowTitle = windowTitle + " Real";
				std::string imWindowTitle = windowTitle + " Imaginary";

				showImg(planes[0], reWindowTitle);
				showImg(planes[1], imWindowTitle);

				break;
			}
		}
	}
	else
		std::cout << "ERROR ( cvComplex::shotComplexImg ) : Input Mat is not complex (m.channels() != 2)" << std::endl;
}




// Opencv extract Magnitude from Complex Matrix
void getMagnitude(cv::Mat& input, cv::Mat& output)
{
	if (input.channels() == 2) // Ensure Complex Matrix
	{
		cv::Mat planes[] = { cv::Mat::zeros(input.rows, input.cols, input.type()), cv::Mat::zeros(input.rows, input.cols, input.type()) };
		cv::split(input, planes);                   // planes[0] = Re(DFT(I), planes[1] = Im(DFT(I))

		cv::magnitude(planes[0], planes[1], output);// planes[0] = magnitude
	}
	else
		std::cout << "ERROR ( cvComplex::shotComplexImg ) : Input Mat is not complex (m.channels() != 2)" << std::endl;
}

// Opencv return real mean value of Matrix
double realMean(cv::Mat& input)
{
	Mat output;
	double realMean = 0;

	if (input.channels() == 2) // Ensure Complex Matrix
	{
		cv::Mat planes[] = { cv::Mat::zeros(input.rows, input.cols, input.type()), cv::Mat::zeros(input.rows, input.cols, input.type()) };
		cv::split(input, planes);                   // planes[0] = Re(DFT(I), planes[1] = Im(DFT(I))

		realMean = mean(planes[0]).val[0];// planes[0] = real-part
	}
	else
		std::cout << "ERROR ( cvComplex::shotComplexImg ) : Input Mat is not complex (m.channels() != 2)" << std::endl;

	return realMean;
}


// Opencv return real mean value of Matrix
double imagMean(cv::Mat& input)
{
	Mat output;
	double imagMean = 0;

	if (input.channels() == 2) // Ensure Complex Matrix
	{
		cv::Mat planes[] = { cv::Mat::zeros(input.rows, input.cols, input.type()), cv::Mat::zeros(input.rows, input.cols, input.type()) };
		cv::split(input, planes);                   // planes[0] = Re(DFT(I), planes[1] = Im(DFT(I))

		imagMean = mean(planes[1]).val[0];// planes[0] = real-part
	}
	else
		std::cout << "ERROR ( cvComplex::shotComplexImg ) : Input Mat is not complex (m.channels() != 2)" << std::endl;

	return imagMean;
}

void printMat(cv::Mat m)
{

	//LOGD("cv::Mat: %s \n", title);
	LOGD("sz_cols:  %d", m.cols);
	LOGD("sz_rows:  %d", m.rows);
	LOGD("deptht: %d ", m.depth());
	LOGD("type: %d", m.type());

}

void showImg(cv::Mat m, std::string windowTitle)
{
   cv::Mat scaledMat, displayMat;
	cv::normalize(m, scaledMat, 0,255, CV_MINMAX);
	scaledMat.convertTo(scaledMat, CV_8U);

	if (gv_cMap >= 0)
		cv::applyColorMap(scaledMat, displayMat, gv_cMap);
   else
   	displayMat = scaledMat;

   cv::namedWindow(windowTitle, cv::WINDOW_NORMAL);
	//cv::setMouseCallback(windowTitle, onMouse, &m);;
	cv::imshow(windowTitle, displayMat);
}
/*
    COLORMAP_AUTUMN = 0,
    COLORMAP_BONE = 1,
    COLORMAP_JET = 2,
    COLORMAP_WINTER = 3,
    COLORMAP_RAINBOW = 4,
    COLORMAP_OCEAN = 5,
    COLORMAP_SUMMER = 6,
    COLORMAP_SPRING = 7,
    COLORMAP_COOL = 8,
    COLORMAP_HSV = 9,
    COLORMAP_PINK = 10,
    COLORMAP_HOT = 11
    */
void setColorMap(int cMap)
{
	if (cMap >= CMAP_MIN && cMap <= CMAP_MAX)
		gv_cMap = cMap;
	else
		std::cout << "ERROR ( cvComplex::setColorMap )  : Invalid Color Map (Valid Values are between " << CMAP_MIN <<" and " << CMAP_MAX << std::endl;
}

void fresnelProp(Mat& input, Mat& output, double zDist, double lambda, double ps_eff){


	// Generate Kernel
	Mat kernel = Mat::zeros(input.rows, input.cols, input.type());
	genFresnelKernel(input, kernel, zDist, lambda, 1.0 / (input.cols*ps_eff), 1.0 / (input.rows*ps_eff));
	//showComplexI_h(kernel, SHOW_COMPLEX_COMPONENTS, "Fresnel Kernel");

	// Convolve with FT of image
	Mat I_h_Ft;
	fft2(input, I_h_Ft);
	fftShift(I_h_Ft, I_h_Ft);

	Mat output_Ft;
	complexMultiply(I_h_Ft, kernel, output_Ft);
	fftShift(output_Ft, output_Ft);

	ifft2(output_Ft, output);

}


void genFresnelKernel(const Mat& input, Mat& kernelMat, double zDist, double lambda, double du, double dv){
	double fx, fy;

    std::complex<double> ii = std::complex<double>(0, 1);

	Mat complexPlanes[] = { Mat::zeros(input.cols, input.rows, CV_64F), Mat::zeros(input.cols, input.rows, CV_64F) };
	for (int i = 0; i < input.cols; i++){
		for (int j = 0; j < input.rows; j++){
			fx = (i - floor(input.cols / 2.)) * du;
			fy = (j - floor(input.rows / 2.)) * dv;
			std::complex<double> cVal = exp(ii * (2 * M_PI / lambda) *zDist) * exp(-ii * M_PI * lambda * zDist * (fx*fx + fy*fy));
			complexPlanes[0].at<double>(i, j) = cVal.real();
			complexPlanes[1].at<double>(i, j) = cVal.imag();
		}
	}
	merge(complexPlanes, 2, kernelMat);
}

}// Read a complex image from two matricies
 void complex_create(cv::Mat&  mat_real, cv::Mat&  mat_imag, cv::Mat& output)
 {

     if (mat_real.rows ==0 || mat_imag.rows==0)
     {
         std::cout << "ERROR - images not found!"<<std::endl;
         return;
     }
     cv::Mat complexPlanes[] = {cv::Mat::zeros(mat_real.rows, mat_real.cols, mat_real.type()), cv::Mat::zeros(mat_real.rows, mat_real.cols, mat_real.type())};
     complexPlanes[0] = mat_real; complexPlanes[1] = mat_imag;
     cv::merge(complexPlanes,2,output);

 }



cv::Mat GetSquareImage( const cv::Mat& img, int target_width)
{
    int width = img.cols,
    height = img.rows;

    cv::Mat square = cv::Mat::zeros( target_width, target_width, img.type() );

    int max_dim = ( width >= height ) ? width : height;
    float scale = ( ( float ) target_width ) / max_dim;
    cv::Rect roi;
    if ( width >= height )
    {
        roi.width = target_width;
        roi.x = 0;
        roi.height = height * scale;
        roi.y = ( target_width - roi.height ) / 2;
    }
    else
    {
        roi.y = 0;
        roi.height = target_width;
        roi.width = width * scale;
        roi.x = ( target_width - roi.width ) / 2;
    }

    cv::resize( img, square( roi ), roi.size() );

    return square;
}

