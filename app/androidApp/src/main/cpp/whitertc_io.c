#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <jni.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdint.h>

#ifdef __ANDROID__
#include <android/log.h>
#define TAG "WhiteRtcNativeIo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#else
#include <stdio.h>
#define TAG "WhiteRtcNativeIo"
#define LOGI(...) do {} while (0)
#define LOGW(...) fprintf(stderr, __VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif

#define MAX_BATCH 32

JNIEXPORT jint JNICALL
Java_com_whitertc_vpn_NativeVectorIo_nativeRecvMmsg(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jobject directBuf,
    jintArray outOffsets,
    jintArray outLengths,
    jint maxPackets,
    jint slotSize
) {
    if (maxPackets <= 0 || maxPackets > MAX_BATCH) {
        maxPackets = MAX_BATCH;
    }

    uint8_t *base = (uint8_t *)(*env)->GetDirectBufferAddress(env, directBuf);
    if (!base) {
        LOGE("nativeRecvMmsg: directBuf address is NULL");
        return -EINVAL;
    }

    jint *lengths = (*env)->GetIntArrayElements(env, outLengths, NULL);
    jint *offsets = (*env)->GetIntArrayElements(env, outOffsets, NULL);
    if (!lengths || !offsets) {
        if (lengths) (*env)->ReleaseIntArrayElements(env, outLengths, lengths, JNI_ABORT);
        if (offsets) (*env)->ReleaseIntArrayElements(env, outOffsets, offsets, JNI_ABORT);
        return -ENOMEM;
    }

    struct mmsghdr msgs[MAX_BATCH];
    struct iovec iovs[MAX_BATCH];
    memset(msgs, 0, sizeof(struct mmsghdr) * maxPackets);

    for (int i = 0; i < maxPackets; i++) {
        int offset = i * slotSize;
        offsets[i] = offset;
        iovs[i].iov_base = base + offset;
        iovs[i].iov_len = slotSize;
        msgs[i].msg_hdr.msg_iov = &iovs[i];
        msgs[i].msg_hdr.msg_iovlen = 1;
    }

    int res = recvmmsg(fd, msgs, (unsigned int)maxPackets, MSG_DONTWAIT, NULL);
    if (res < 0) {
        int err = errno;
        (*env)->ReleaseIntArrayElements(env, outOffsets, offsets, JNI_ABORT);
        (*env)->ReleaseIntArrayElements(env, outLengths, lengths, JNI_ABORT);
        if (err == EAGAIN || err == EWOULDBLOCK) {
            return 0;
        }
        return -err;
    }

    for (int i = 0; i < res; i++) {
        lengths[i] = (jint)msgs[i].msg_len;
    }

    (*env)->ReleaseIntArrayElements(env, outOffsets, offsets, 0);
    (*env)->ReleaseIntArrayElements(env, outLengths, lengths, 0);
    return res;
}

JNIEXPORT jint JNICALL
Java_com_whitertc_vpn_NativeVectorIo_nativeSendMmsg(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jobject directBuf,
    jintArray inOffsets,
    jintArray inLengths,
    jint count
) {
    if (count <= 0 || count > MAX_BATCH) {
        count = MAX_BATCH;
    }

    uint8_t *base = (uint8_t *)(*env)->GetDirectBufferAddress(env, directBuf);
    if (!base) {
        return -EINVAL;
    }

    jint *lengths = (*env)->GetIntArrayElements(env, inLengths, NULL);
    jint *offsets = (*env)->GetIntArrayElements(env, inOffsets, NULL);
    if (!lengths || !offsets) {
        if (lengths) (*env)->ReleaseIntArrayElements(env, inLengths, lengths, JNI_ABORT);
        if (offsets) (*env)->ReleaseIntArrayElements(env, inOffsets, offsets, JNI_ABORT);
        return -ENOMEM;
    }

    struct mmsghdr msgs[MAX_BATCH];
    struct iovec iovs[MAX_BATCH];
    memset(msgs, 0, sizeof(struct mmsghdr) * count);

    for (int i = 0; i < count; i++) {
        iovs[i].iov_base = base + offsets[i];
        iovs[i].iov_len = (size_t)lengths[i];
        msgs[i].msg_hdr.msg_iov = &iovs[i];
        msgs[i].msg_hdr.msg_iovlen = 1;
    }

    int res = sendmmsg(fd, msgs, (unsigned int)count, MSG_DONTWAIT);
    int err = (res < 0) ? errno : 0;

    (*env)->ReleaseIntArrayElements(env, inOffsets, offsets, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, inLengths, lengths, JNI_ABORT);

    if (res < 0) {
        if (err == EAGAIN || err == EWOULDBLOCK) {
            return 0;
        }
        return -err;
    }
    return res;
}

JNIEXPORT jint JNICALL
Java_com_whitertc_vpn_NativeVectorIo_nativeWriteTunBatch(
    JNIEnv *env,
    jobject thiz,
    jint tunFd,
    jobject directBuf,
    jintArray inOffsets,
    jintArray inLengths,
    jint count
) {
    if (count <= 0) return 0;
    if (count > MAX_BATCH) count = MAX_BATCH;

    uint8_t *base = (uint8_t *)(*env)->GetDirectBufferAddress(env, directBuf);
    if (!base) return -EINVAL;

    jint *lengths = (*env)->GetIntArrayElements(env, inLengths, NULL);
    jint *offsets = (*env)->GetIntArrayElements(env, inOffsets, NULL);
    if (!lengths || !offsets) {
        if (lengths) (*env)->ReleaseIntArrayElements(env, inLengths, lengths, JNI_ABORT);
        if (offsets) (*env)->ReleaseIntArrayElements(env, inOffsets, offsets, JNI_ABORT);
        return -ENOMEM;
    }

    int writtenCount = 0;
    for (int i = 0; i < count; i++) {
        int len = lengths[i];
        if (len <= 0) continue;
        int off = offsets[i];
        ssize_t n = write(tunFd, base + off, (size_t)len);
        if (n > 0) {
            writtenCount++;
        } else {
            int err = errno;
            if (err == EAGAIN || err == EWOULDBLOCK) {
                break;
            }
            LOGW("nativeWriteTunBatch write failed: errno=%d (%s)", err, strerror(err));
            break;
        }
    }

    (*env)->ReleaseIntArrayElements(env, inOffsets, offsets, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, inLengths, lengths, JNI_ABORT);
    return writtenCount;
}

JNIEXPORT jint JNICALL
Java_com_whitertc_vpn_NativeVectorIo_nativeGetFd(
    JNIEnv *env,
    jobject thiz,
    jobject fileDescriptor
) {
    if (!fileDescriptor) return -1;
    jclass fdClass = (*env)->GetObjectClass(env, fileDescriptor);
    if (!fdClass) return -1;

    // First attempt Android's getInt$() method
    jmethodID mid = (*env)->GetMethodID(env, fdClass, "getInt$", "()I");
    if (mid) {
        return (*env)->CallIntMethod(env, fileDescriptor, mid);
    }
    (*env)->ExceptionClear(env);

    // Standard OpenJDK "fd" field
    jfieldID openJdkFid = (*env)->GetFieldID(env, fdClass, "fd", "I");
    if (openJdkFid) {
        return (*env)->GetIntField(env, fileDescriptor, openJdkFid);
    }
    (*env)->ExceptionClear(env);

    // Fallback to "descriptor" field
    jfieldID fid = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
    if (fid) {
        return (*env)->GetIntField(env, fileDescriptor, fid);
    }
    (*env)->ExceptionClear(env);

    return -1;
}

JNIEXPORT jint JNICALL
Java_com_whitertc_vpn_NativeVectorIo_nativeGetChannelFd(
    JNIEnv *env,
    jobject thiz,
    jobject channel
) {
    if (!channel) return -1;
    jclass chClass = (*env)->GetObjectClass(env, channel);
    if (!chClass) return -1;

    // Check OpenJDK DatagramChannelImpl.fdVal
    jfieldID fdValFid = (*env)->GetFieldID(env, chClass, "fdVal", "I");
    if (fdValFid) {
        return (*env)->GetIntField(env, channel, fdValFid);
    }
    (*env)->ExceptionClear(env);

    jfieldID fdFid = (*env)->GetFieldID(env, chClass, "fd", "Ljava/io/FileDescriptor;");
    if (!fdFid) {
        (*env)->ExceptionClear(env);
        return -1;
    }
    jobject fdObj = (*env)->GetObjectField(env, channel, fdFid);
    if (!fdObj) return -1;

    return Java_com_whitertc_vpn_NativeVectorIo_nativeGetFd(env, thiz, fdObj);
}

// JNI exports for com.whitertc.core.transport.NativeVectorIo:
JNIEXPORT jint JNICALL
Java_com_whitertc_core_transport_NativeVectorIo_nativeRecvMmsg(
    JNIEnv *env, jobject thiz, jint fd, jobject directBuf,
    jintArray outOffsets, jintArray outLengths, jint maxPackets, jint slotSize
) {
    return Java_com_whitertc_vpn_NativeVectorIo_nativeRecvMmsg(env, thiz, fd, directBuf, outOffsets, outLengths, maxPackets, slotSize);
}

JNIEXPORT jint JNICALL
Java_com_whitertc_core_transport_NativeVectorIo_nativeSendMmsg(
    JNIEnv *env, jobject thiz, jint fd, jobject directBuf,
    jintArray inOffsets, jintArray inLengths, jint count
) {
    return Java_com_whitertc_vpn_NativeVectorIo_nativeSendMmsg(env, thiz, fd, directBuf, inOffsets, inLengths, count);
}

JNIEXPORT jint JNICALL
Java_com_whitertc_core_transport_NativeVectorIo_nativeWriteTunBatch(
    JNIEnv *env, jobject thiz, jint tunFd, jobject directBuf,
    jintArray inOffsets, jintArray inLengths, jint count
) {
    return Java_com_whitertc_vpn_NativeVectorIo_nativeWriteTunBatch(env, thiz, tunFd, directBuf, inOffsets, inLengths, count);
}

JNIEXPORT jint JNICALL
Java_com_whitertc_core_transport_NativeVectorIo_nativeGetFd(
    JNIEnv *env, jobject thiz, jobject fileDescriptor
) {
    return Java_com_whitertc_vpn_NativeVectorIo_nativeGetFd(env, thiz, fileDescriptor);
}

JNIEXPORT jint JNICALL
Java_com_whitertc_core_transport_NativeVectorIo_nativeGetChannelFd(
    JNIEnv *env, jobject thiz, jobject channel
) {
    return Java_com_whitertc_vpn_NativeVectorIo_nativeGetChannelFd(env, thiz, channel);
}

