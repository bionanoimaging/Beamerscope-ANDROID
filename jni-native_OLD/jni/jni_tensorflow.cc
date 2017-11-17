/*
Copyright 2016 Narrative Nights Inc. All Rights Reserved.
Copyright 2015 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

#include "common.h"

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>

#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <queue>
#include <sstream>
#include <string>

#include "tensorflow/core/framework/step_stats.pb.h"
#include "tensorflow/core/framework/tensor.h"
#include "tensorflow/core/framework/types.pb.h"
#include "tensorflow/core/lib/strings/stringprintf.h"
#include "tensorflow/core/platform/env.h"
#include "tensorflow/core/platform/logging.h"
#include "tensorflow/core/platform/mutex.h"
#include "tensorflow/core/platform/types.h"
#include "tensorflow/core/public/session.h"
#include "tensorflow/core/util/stat_summarizer.h"
#include "jni_utils.h"

#include "opencv2/core.hpp"

#include "cvComplex.h"
#include "common.h"

static const int NUM_OUTPUT = 48;
static const int NUM_INPUT = 4096;
static std::unique_ptr<tensorflow::Session> session;

static bool g_compute_graph_initialized = false;
using namespace tensorflow;
using namespace cv;
using namespace std;


JNIEXPORT jint JNICALL
Java_de_beamerscope_nativepart_NativePart_init(JNIEnv* env,
                        jobject thiz,
                        jobject java_asset_manager,
                        jstring model) {

	if (g_compute_graph_initialized) {
		LOG(INFO) << "Compute graph already loaded. skipping.";
		return 0;
	}

	const char* const model_cstr = env->GetStringUTFChars(model, NULL);

	LOG(INFO) << "Loading Tensorflow.";
	LOG(INFO) << "Making new SessionOptions.";

	tensorflow::SessionOptions options;
	tensorflow::ConfigProto& config = options.config;
	LOG(INFO) << "Got config, " << config.device_count_size() << " devices";
	session.reset(tensorflow::NewSession(options));
	LOG(INFO) << "Session created.";

	tensorflow::GraphDef graph_def;

	LOG(INFO) << "Graph created.";

	AAssetManager* const asset_manager =
		AAssetManager_fromJava(env, java_asset_manager);

	LOG(INFO) << "Acquired AssetManager.";

	LOG(INFO) << "Reading file to proto: " << model_cstr;

	ReadFileToProtoOrDie(asset_manager, model_cstr, &graph_def);

	LOG(INFO) << "Creating session.";

	tensorflow::Status s = session->Create(graph_def);

	if (!s.ok()) {
		LOG(ERROR) << "Could not create Tensorflow Graph: " << s;
		return -1;
	}

	// Clear the proto to save memory space.
	graph_def.Clear();

	LOG(INFO) << "Tensorflow graph loaded from: " << model_cstr;

	g_compute_graph_initialized = true;

	return 0;
}

static Mat process(cv::Mat& inputMat) {
	// Create input tensor
	Tensor input_tensor( tensorflow::DT_FLOAT,
						 tensorflow::TensorShape( {1, NUM_INPUT} ) );

	auto input_tensor_mapped = input_tensor.tensor<float, 2>();

    LOGD("*********inputMat**********");
    printMat(inputMat);

	for(int i= 0; i<NUM_INPUT; ++i) {
		double value = (double) inputMat.at<double>(i, 0);
		if (value != value){ //check for NaN
		    value = 0;}
		//LOGD("input:  %d, %f", i, value);
		input_tensor_mapped(0, i) = (float)value;
	}

	LOGI("Start computing.");

    //string InputName = "input";
    //string OutputName = "output";

    string InputName = "input";
    string OutputName = "output";


	std::vector<Tensor> output_tensors;

	// Actually run the image through the model.
	tensorflow::Status run_status  = session->Run({{InputName,input_tensor}},{OutputName},{},&output_tensors);


	LOGI("End computing.");

	if (!run_status.ok()) {
	    LOGE("Error during inference:");
	    LOG(ERROR) << "Error during inference: " << run_status;
		//return -1;
	}

	// finding the labels for prediction
	LOGD("final output size= %d", (int)output_tensors.size());

	// Find best score digit
	Tensor& output_tensor = output_tensors[0];
	tensorflow::TTypes<float>::Flat output_flat = output_tensor.flat<float>();

	// Cast double-vector to MAT-object
	Mat outputmat = Mat::zeros(1, 48, CV_64F );

    // Normalize ouput values
	double max_value = -1E10;
	double min_value = 1E10;

	for(int i=0; i<NUM_OUTPUT; ++i) {
		outputmat.at<double>(0, i) = (double)output_flat(i);
		if (max_value < outputmat.at<double>(0, i)){
		    max_value = outputmat.at<double>(0, i);
		}
		if (min_value > outputmat.at<double>(0, i)){
            min_value = outputmat.at<double>(0, i);
        }
		LOGD("output:  %d, %f, %f", i, output_flat(i), outputmat.at<double>(0, i));
	}

	for(int i= 0; i<NUM_OUTPUT; ++i) {
        outputmat.at<double>(0, i) = (outputmat.at<double>(0, i) - min_value)/max_value;
		//LOGD("outputmat:  %d, %f", i, outputmat.at<double>(0, i));
    }


	 return outputmat;

}

JNIEXPORT void JNICALL
Java_de_beamerscope_nativepart_NativePart_getOptimizedParams
(JNIEnv* env, jobject thiz, jlong addrInputMat, jlong addrOutputMat) {

    LOG(INFO) << "*********start Illparameter**********";

    // Assign Pointer of Java object to native Mat object
    Mat& inputMat  = *(Mat*)addrInputMat;
    Mat& outputMat  = *(Mat*)addrOutputMat;

    // Display Size and Information of inputMat; Should be of size Mat(rows=4096, cols=1)
    printMat(inputMat);

    // Use inputMat to get the output parameters from the trained network
    outputMat = process(inputMat);

}
