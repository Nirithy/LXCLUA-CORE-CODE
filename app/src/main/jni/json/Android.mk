LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_MODULE     := json
LOCAL_SRC_FILES  := json.c \
        json-decode.c \
        json-encode.c
LOCAL_CFLAGS := -std=c17 -O3 -flto \
                -funroll-loops -fomit-frame-pointer \
                -ffunction-sections -fdata-sections \
                -fstrict-aliasing
LOCAL_STATIC_LIBRARIES := LXCLuaCore

# 系统库链接
LOCAL_LDLIBS += -L$(SYSROOT)/usr/lib -llog -ldl -lz
include $(BUILD_SHARED_LIBRARY)
