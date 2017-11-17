#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
#include <opencv2/features2d.hpp>
#include <vector>
#include <iostream>
#include <string>
#include <stdio.h>
#include <jni.h>
#include "common.h"

#include "jni_qDPC.h"
#include "jni_illparameter.h"



using namespace std;
using namespace cv;

extern "C" {




JNIEXPORT void JNICALL Java_de_beamerscope_nativepart_NativePart_qDPC
 (JNIEnv *, jclass, jlong addrI11, jlong addrI12, jlong addrI21, jlong addrI22, jlong addrTrans1,
    jlong addrTrans2, jlong addrOutputMat, jlong addrOutputMatDiffX, jlong addrOutputMatDiffY){
    LOGD("*********start QPDC**********");

    Mat& MatI11  = *(Mat*)addrI11;
    Mat& MatI12  = *(Mat*)addrI12;
    Mat& MatI21  = *(Mat*)addrI21;
    Mat& MatI22  = *(Mat*)addrI22;
    Mat& MatTrans1  = *(Mat*)addrTrans1;
    Mat& MatTrans2  = *(Mat*)addrTrans2;
    Mat& MatOutput  = *(Mat*)addrOutputMat;
    Mat& MatDiffX  = *(Mat*)addrOutputMatDiffX;
    Mat& MatDiffY = *(Mat*)addrOutputMatDiffY;

    LOGD("*********Variables Assigned**********");
    MatOutput = qDPC(MatI11, MatI12, MatI21, MatI22, MatTrans1, MatTrans2);
    MatDiffX = calcDPC(MatI11, MatI12);
    MatDiffY = calcDPC(MatI21, MatI22);

    LOGD("*********Done**********");

  }


JNIEXPORT void JNICALL Java_de_beamerscope_nativepart_NativePart_getFFTVector
        (JNIEnv *, jclass, jlong addrInputMat, jlong addrOutputMat){

    LOGD("*********start getFFTVector**********");
    Mat& inputImage  = *(Mat*)addrInputMat;
    Mat& outputFFTVector  = *(Mat*)addrOutputMat;

    outputFFTVector = getFFTVector(inputImage);
    }


JNIEXPORT void JNICALL Java_de_beamerscope_nativepart_NativePart_getAngleVector
        (JNIEnv *, jclass, jlong addrInputMat, jlong addrOutputMat){

    LOGD("*********start getAngleVector**********");
    Mat& inputImage  = *(Mat*)addrInputMat;
    Mat& outputAngleVector  = *(Mat*)addrOutputMat;

    outputAngleVector = getAngleVector(inputImage);
    }


}


