//
// Created by Benedict Diederich on 18.08.16.
//

//common.h



#include <jni.h>
#include <android/log.h>

#define  LOG_TAG    "TAG"

#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)


#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)



//#define TENSORFLOW_METHOD(METHOD_NAME) Java_de_beamerscope_nativepart_NativePart_##METHOD_NAME  // NOLINT



extern "C" {

/*
 * Class:     de_beamerscope_nativepart_NativePart
 * Method:    qDPC
 * Signature: (JJD)V
 */
JNIEXPORT void JNICALL Java_de_beamerscope_nativepart_NativePart_qDPC
 (JNIEnv *, jclass, jlong addrI11, jlong addrI12, jlong addrI21, jlong addrI22, jlong addrTrans1,
    jlong addrTrans2, jlong addrOutputMat);


JNIEXPORT void JNICALL Java_de_beamerscope_nativepart_NativePart_getFFTVector
        (JNIEnv *, jclass, jlong, jlong);

JNIEXPORT void JNICALL Java_de_beamerscope_nativepart_NativePart_getAngleVector
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     de_beamerscope_nativepart_NativePart
 * Method:    getIllPattern
 * Signature: (JJ)V
 */

 // TENSORFLOW PART
JNIEXPORT jint JNICALL Java_de_beamerscope_nativepart_NativePart_init
    (JNIEnv* env, jobject thiz, jobject java_asset_manager, jstring model);

JNIEXPORT void JNICALL Java_de_beamerscope_nativepart_NativePart_getOptimizedParams
    (JNIEnv* env, jobject thiz, jlong addrInputMat, jlong addrOutputMat);

}


