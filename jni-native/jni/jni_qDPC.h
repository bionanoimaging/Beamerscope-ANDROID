//jni_qDPC.h

#include <time.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
#include <iostream>
#include <string>
#include <stdio.h>
#include <fstream>
#include <vector>


using namespace std;
using namespace cv;



extern "C" {

Mat qDPC(Mat dpc11, Mat dpc12, Mat dpc21, Mat dpc22, Mat trans1, Mat trans2);
Mat calcDPC( Mat image1, Mat image2);

}




