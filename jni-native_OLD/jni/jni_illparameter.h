//
// Created by Benedict Diederich on 25.08.16.
//

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>

#ifndef BEAMERSCOPEILLOPTIMIZATION_JNI_ILLPARAMETER_H
#define BEAMERSCOPEILLOPTIMIZATION_JNI_ILLPARAMETER_H

#endif //BEAMERSCOPEILLOPTIMIZATION_JNI_ILLPARAMETER_H


#ifdef __cplusplus
extern "C" {
#endif


cv::Mat getNNParameter (cv::Mat input);
cv::Mat getFFTVector(cv::Mat input);
cv::Mat getAngleVector(cv::Mat input);



#ifdef __cplusplus
}
#endif
