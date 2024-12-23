#include <jni.h>
#include <string>
#include "tun2proxy.h"
#include <dlfcn.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_lucas_vpnapp_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

static void* t2p_handle = NULL;

extern "C" JNIEXPORT jint JNICALL
Java_com_lucas_vpnapp_MainActivity_startTun2proxy(
        JNIEnv *env, jclass clazz,
        jstring proxy_url,
        jint tun_fd, jboolean close_fd_on_drop,
        jchar tun_mtu, jint verbosity,
        jint dns_strategy) {
    if (t2p_handle) {
        return -3;
    }

    // load the tun2proxy library
    t2p_handle = dlopen("libtun2proxy.so", RTLD_LAZY);
    if (!t2p_handle) {
        return -1;
    }

    // get the tun2proxy_with_fd_run function pointer
    pfn_tun2proxy_with_fd_run t2p_run = (pfn_tun2proxy_with_fd_run) dlsym(t2p_handle, "tun2proxy_with_fd_run");
    if (!t2p_run) {
        return -2;
    }

    // call the tun2proxy_with_fd_run function
    const char *nativeString = env->GetStringUTFChars(proxy_url, NULL);
    int r = t2p_run(nativeString, tun_fd, close_fd_on_drop, false, tun_mtu,
                    (Tun2proxyDns)verbosity, (Tun2proxyVerbosity)dns_strategy);
    env->ReleaseStringUTFChars(proxy_url, nativeString);

    // dlclose(t2p_handle);
    t2p_handle = NULL;

    return r;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_lucas_vpnapp_MainActivity_stopTun2proxy(JNIEnv *env, jclass clazz) {
    if (!t2p_handle) {
        return -1;
    }

    pfn_tun2proxy_with_fd_stop tun_stop = (pfn_tun2proxy_with_fd_stop) dlsym(t2p_handle, "tun2proxy_with_fd_stop");
    if (!tun_stop) {
        return -2;
    }

    int r = tun_stop();

    return r;
}
