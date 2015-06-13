#include <stdlib.h>
#include <jni.h>
#include "droidtop.h"

/* Header for class com_jackos2500_droidtop_DroidtopNative */
#ifndef _Included_com_jackos2500_droidtop_DroidtopNative
#define _Included_com_jackos2500_droidtop_DroidtopNative
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_com_jackos2500_droidtop_DroidtopNative_init(JNIEnv *env, jclass cls, jint realWidth, jint realHeight, jint width, jint height) {
	return init(realWidth, realHeight, width, height);
}
JNIEXPORT void JNICALL Java_com_jackos2500_droidtop_DroidtopNative_update(JNIEnv *env, jclass cls) {
	update();
}
JNIEXPORT void JNICALL Java_com_jackos2500_droidtop_DroidtopNative_close(JNIEnv *env, jclass cls) {
	close();
}

JNIEXPORT jboolean JNICALL Java_com_jackos2500_droidtop_DroidtopNative_grab(JNIEnv *env, jclass cls, jint fd) {
	return grab(fd);
}
JNIEXPORT jboolean JNICALL Java_com_jackos2500_droidtop_DroidtopNative_ungrab(JNIEnv *env, jclass cls, jint fd) {
	return ungrab(fd);
}

JNIEXPORT jint JNICALL Java_com_jackos2500_droidtop_DroidtopNative_openFile(JNIEnv *env, jclass cls, jstring path_) {
	const char *path;
	
	path = (env)->GetStringUTFChars(path_, NULL);
	
	return open_file(path);
}
JNIEXPORT jint JNICALL Java_com_jackos2500_droidtop_DroidtopNative_closeFile(JNIEnv *env, jclass cls, jint fd) {
	return close_file(fd);
}

JNIEXPORT jboolean JNICALL Java_com_jackos2500_droidtop_DroidtopNative_isMouse(JNIEnv *env, jclass cls, jint fd) {
	return is_mouse(fd);
}
JNIEXPORT jboolean JNICALL Java_com_jackos2500_droidtop_DroidtopNative_isKeyboard(JNIEnv *env, jclass cls, jint fd) {
	return is_keyboard(fd);
}

#ifdef __cplusplus
}
#endif
#endif
