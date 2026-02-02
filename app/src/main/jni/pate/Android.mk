LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := pate
LOCAL_CFLAGS := -std=c23 -O3 -flto \
                -funroll-loops -fomit-frame-pointer \
                -ffunction-sections -fdata-sections \
                -fstrict-aliasing
LOCAL_CFLAGS += -g0 -DNDEBUG -DLUA_CORE

LOCAL_C_INCLUDES := $(LOCAL_PATH)/../lua

LOCAL_SRC_FILES := pate.c

LOCAL_STATIC_LIBRARIES := lua

LOCAL_LDLIBS += -llog

include $(BUILD_SHARED_LIBRARY)
