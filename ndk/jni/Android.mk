LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := droidtop
LOCAL_CPPFLAGS  := -Wall -Wextra
LOCAL_SRC_FILES := jni.cpp util.cpp droidtop.cpp
LOCAL_LDLIBS    := -lGLESv3 -llog

include $(BUILD_SHARED_LIBRARY)
