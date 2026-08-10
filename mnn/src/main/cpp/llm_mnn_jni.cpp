// Ported to RikkaLLM from MnnLlmChat:
//   - Firebase analytics reporting (ReportLlmSetConfigToFirebase / CrashReportContext) removed
//   - audio JNI (setWavformCallbackNative / updateEnableAudioOutputNative) removed
//   - benchmark JNI (runBenchmarkNative) removed
// JNI symbol names keep the com.alibaba.mnnllm.android.llm.LlmSession package on purpose:
// they are hardcoded on the native side.

#include <android/log.h>
#include <jni.h>
#include <string>
#include <utility>
#include <vector>
#include "mls_log.h"
#include "nlohmann/json.hpp"
#include "llm_session.h"

using MNN::Transformer::Llm;
using json = nlohmann::json;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "MNN_DEBUG", "JNI_OnLoad");
    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "MNN_DEBUG", "JNI_OnUnload");
}

JNIEXPORT jlong JNICALL Java_com_alibaba_mnnllm_android_llm_LlmSession_initNative(JNIEnv *env,
                                                                                  jobject thiz,
                                                                                  jstring modelDir,
                                                                                  jobject chat_history,
                                                                                  jstring mergeConfigStr,
                                                                                  jstring configJsonStr) {
    const char *model_dir = env->GetStringUTFChars(modelDir, nullptr);
    auto model_dir_str = std::string(model_dir);
    const char *config_json_cstr = env->GetStringUTFChars(configJsonStr, nullptr);
    const char *merged_config_cstr = env->GetStringUTFChars(mergeConfigStr, nullptr);
    json merged_config = json::parse(merged_config_cstr);
    json extra_json_config = json::parse(config_json_cstr);
    env->ReleaseStringUTFChars(modelDir, model_dir);
    env->ReleaseStringUTFChars(configJsonStr, config_json_cstr);
    env->ReleaseStringUTFChars(mergeConfigStr, merged_config_cstr);
    MNN_DEBUG("createLLM BeginLoad %s", model_dir);
    std::vector<std::string> history;
    history.clear();
    if (chat_history != nullptr) {
        jclass listClass = env->GetObjectClass(chat_history);
        jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
        jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");
        jint listSize = env->CallIntMethod(chat_history, sizeMethod);
        for (jint i = 0; i < listSize; i++) {
            jobject element = env->CallObjectMethod(chat_history, getMethod, i);
            const char *elementCStr = env->GetStringUTFChars((jstring) element, nullptr);
            history.emplace_back(elementCStr);
            env->ReleaseStringUTFChars((jstring) element, elementCStr);
            env->DeleteLocalRef(element);
        }
    }
    auto llm_session = new mls::LlmSession(model_dir_str, merged_config, extra_json_config,
                                           history);
    bool load_success = llm_session->Load();
    MNN_DEBUG("LIFECYCLE: LlmSession CREATED at %p, load_success=%d", llm_session, load_success);
    if (!load_success || !llm_session->isModelReady()) {
        std::string err_msg = llm_session->getLastLoadError();
        if (err_msg.empty()) {
            err_msg = "Model load failed for config: " + model_dir_str;
        }
        MNN_DEBUG("Model load failed, cleaning up LlmSession: %s", err_msg.c_str());
        delete llm_session;
        jclass exClass = env->FindClass("java/lang/IllegalStateException");
        if (exClass) {
            env->ThrowNew(exClass, err_msg.c_str());
        }
        return 0;
    }
    MNN_DEBUG("createLLM EndLoad %ld ", reinterpret_cast<jlong>(llm_session));
    return reinterpret_cast<jlong>(llm_session);
}


JNIEXPORT jobject JNICALL Java_com_alibaba_mnnllm_android_llm_LlmSession_submitNative(JNIEnv *env,
                                                                                      jobject thiz,
                                                                                      jlong llmPtr,
                                                                                      jstring inputStr,
                                                                                      jboolean keepHistory,
                                                                                      jobject
                                                                                      progressListener) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(llmPtr);
    if (!llm) {
        // Return a HashMap with error info to match the Java signature
        jclass hashMapClass = env->FindClass("java/util/HashMap");
        jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
        jmethodID putMethod = env->GetMethodID(hashMapClass, "put",
                                               "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        jobject hashMap = env->NewObject(hashMapClass, hashMapInit);
        env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("error"),
                              env->NewStringUTF("Failed, Chat is not ready!"));
        return hashMap;
    }
    const char *input_str = env->GetStringUTFChars(inputStr, nullptr);
    jclass progressListenerClass = env->GetObjectClass(progressListener);
    jmethodID onProgressMethod = env->GetMethodID(progressListenerClass, "onProgress",
                                                  "(Ljava/lang/String;)Z");
    if (!onProgressMethod) {
        MNN_DEBUG("ProgressListener onProgress method not found.");
    }
    auto *context = llm->Response(input_str, [&, progressListener, onProgressMethod](
            const std::string &response, bool is_eop) {
        if (progressListener && onProgressMethod) {
            jstring javaString = is_eop ? nullptr : env->NewStringUTF(response.c_str());
            jboolean user_stop_requested = env->CallBooleanMethod(progressListener,
                                                                  onProgressMethod, javaString);
            env->DeleteLocalRef(javaString);
            return (bool) user_stop_requested;
        } else {
            return true;
        }
    });
    int64_t prompt_len = 0;
    int64_t decode_len = 0;
    int64_t vision_time = 0;
    int64_t audio_time = 0;
    int64_t prefill_time = 0;
    int64_t decode_time = 0;
    if (context) {
        prompt_len += context->prompt_len;
        decode_len += context->gen_seq_len;
        vision_time += context->vision_us;
        audio_time += context->audio_us;
        prefill_time += context->prefill_us;
        decode_time += context->decode_us;
    }
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put",
                                           "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject hashMap = env->NewObject(hashMapClass, hashMapInit);

    // Add metrics to the HashMap
    env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("prompt_len"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"),
                                                          "<init>", "(J)V"), prompt_len));
    env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("decode_len"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"),
                                                          "<init>", "(J)V"), decode_len));
    env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("vision_time"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"),
                                                          "<init>", "(J)V"), vision_time));
    env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("audio_time"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"),
                                                          "<init>", "(J)V"), audio_time));
    env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("prefill_time"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"),
                                                          "<init>", "(J)V"), prefill_time));
    env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("decode_time"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"),
                                                          "<init>", "(J)V"), decode_time));
    return hashMap;
}



// Supports inference over a complete caller-provided history
JNIEXPORT jobject JNICALL Java_com_alibaba_mnnllm_android_llm_LlmSession_submitFullHistoryNative(
        JNIEnv *env,
        jobject thiz,
        jlong llmPtr,
        jobject historyList,  // List<Pair<String, String>>
        jobject progressListener
) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(llmPtr);
    if (!llm) {
        // Return a HashMap with error info to match the Java signature
        jclass hashMapClass = env->FindClass("java/util/HashMap");
        jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
        jmethodID putMethod = env->GetMethodID(hashMapClass, "put",
                                               "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        jobject hashMap = env->NewObject(hashMapClass, hashMapInit);
        env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("error"),
                              env->NewStringUTF("Failed, Chat is not ready!"));
        return hashMap;
    }

    // Parse Java List<Pair<String, String>> into a C++ vector
    std::vector<mls::PromptItem> history;

    jclass listClass = env->GetObjectClass(historyList);
    jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
    jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

    jint listSize = env->CallIntMethod(historyList, sizeMethod);

    jclass pairClass = env->FindClass("android/util/Pair");
    if (pairClass == nullptr) {
        MNN_DEBUG("Failed to find android.util.Pair class");
        // Return a HashMap with error info to match the Java signature
        jclass hashMapClass = env->FindClass("java/util/HashMap");
        jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
        jmethodID putMethod = env->GetMethodID(hashMapClass, "put",
                                               "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        jobject hashMap = env->NewObject(hashMapClass, hashMapInit);
        env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("error"),
                              env->NewStringUTF("Failed to find android.util.Pair class"));
        return hashMap;
    }
    jfieldID firstField = env->GetFieldID(pairClass, "first", "Ljava/lang/Object;");
    jfieldID secondField = env->GetFieldID(pairClass, "second", "Ljava/lang/Object;");

    for (jint i = 0; i < listSize; i++) {
        jobject pairObj = env->CallObjectMethod(historyList, getMethod, i);
        if (pairObj == nullptr) {
            continue;
        }
        jobject roleObj = env->GetObjectField(pairObj, firstField);
        jobject contentObj = env->GetObjectField(pairObj, secondField);

        const char *role = nullptr;
        const char *content = nullptr;
        if (roleObj != nullptr) {
            role = env->GetStringUTFChars((jstring) roleObj, nullptr);
        }
        if (contentObj != nullptr) {
            content = env->GetStringUTFChars((jstring) contentObj, nullptr);
        }

        if (role && content) {
            history.emplace_back(std::string(role), std::string(content));
        }

        if (role) {
            env->ReleaseStringUTFChars((jstring) roleObj, role);
        }
        if (content) {
            env->ReleaseStringUTFChars((jstring) contentObj, content);
        }
        env->DeleteLocalRef(pairObj);
        if (roleObj) {
            env->DeleteLocalRef(roleObj);
        }
        if (contentObj) {
            env->DeleteLocalRef(contentObj);
        }
    }

    jclass progressListenerClass = env->GetObjectClass(progressListener);
    jmethodID onProgressMethod = env->GetMethodID(progressListenerClass, "onProgress",
                                                  "(Ljava/lang/String;)Z");

    if (!onProgressMethod) {
        MNN_DEBUG("ProgressListener onProgress method not found.");
    }

    auto *context = llm->ResponseWithHistory(history, [&, progressListener, onProgressMethod](
            const std::string &response, bool is_eop) {
        if (progressListener && onProgressMethod) {
            jstring javaString = is_eop ? nullptr : env->NewStringUTF(response.c_str());
            jboolean user_stop_requested = env->CallBooleanMethod(progressListener,
                                                                  onProgressMethod, javaString);
            if (javaString) {
                env->DeleteLocalRef(javaString);
            }
            return (bool) user_stop_requested;
        } else {
            return true;
        }
    });

    int64_t prompt_len = 0;
    int64_t decode_len = 0;
    int64_t vision_time = 0;
    int64_t audio_time = 0;
    int64_t prefill_time = 0;
    int64_t decode_time = 0;

    if (context) {
        prompt_len += context->prompt_len;
        decode_len += context->gen_seq_len;
        vision_time += context->vision_us;
        audio_time += context->audio_us;
        prefill_time += context->prefill_us;
        decode_time += context->decode_us;
    }

    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID hashMapPut = env->GetMethodID(hashMapClass, "put",
                                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject hashMap = env->NewObject(hashMapClass, hashMapInit);

    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longInit = env->GetMethodID(longClass, "<init>", "(J)V");

    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("prompt_len"),
                          env->NewObject(longClass, longInit, prompt_len));
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("decode_len"),
                          env->NewObject(longClass, longInit, decode_len));
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("vision_time"),
                          env->NewObject(longClass, longInit, vision_time));
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("audio_time"),
                          env->NewObject(longClass, longInit, audio_time));
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("prefill_time"),
                          env->NewObject(longClass, longInit, prefill_time));
    env->CallObjectMethod(hashMap, hashMapPut, env->NewStringUTF("decode_time"),
                          env->NewObject(longClass, longInit, decode_time));

    return hashMap;
}



JNIEXPORT void JNICALL
Java_com_alibaba_mnnllm_android_llm_LlmSession_resetNative(JNIEnv *env, jobject thiz,
                                                           jlong object_ptr) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(object_ptr);
    if (llm) {
        MNN_DEBUG("RESET");
        llm->Reset();
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_alibaba_mnnllm_android_llm_LlmSession_getDebugInfoNative(JNIEnv *env, jobject thiz,
                                                                  jlong objecPtr) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(objecPtr);
    if (llm == nullptr) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(llm->getDebugInfo().c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_alibaba_mnnllm_android_llm_LlmSession_dumpConfigNative(JNIEnv *env, jobject thiz,
                                                                jlong objecPtr) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(objecPtr);
    if (llm == nullptr) {
        return env->NewStringUTF("{}");
    }
    return env->NewStringUTF(llm->dumpConfig().c_str());
}

JNIEXPORT void JNICALL Java_com_alibaba_mnnllm_android_llm_LlmSession_releaseNative(JNIEnv *env,
                                                                                    jobject thiz,
                                                                                    jlong objecPtr) {
    MNN_DEBUG("LIFECYCLE: About to DESTROY LlmSession at %p", reinterpret_cast<void*>(objecPtr));
    auto *llm = reinterpret_cast<mls::LlmSession *>(objecPtr);
    delete llm;
    MNN_DEBUG("LIFECYCLE: LlmSessionInner DESTROYED at %p", reinterpret_cast<void*>(objecPtr));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_alibaba_mnnllm_android_llm_LlmSession_updateMaxNewTokensNative(JNIEnv *env, jobject thiz,
                                                                        jlong llm_ptr,
                                                                        jint max_new_tokens) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(llm_ptr);
    if (llm) {
        llm->SetMaxNewTokens(max_new_tokens);
    }

}
extern "C"
JNIEXPORT void JNICALL
Java_com_alibaba_mnnllm_android_llm_LlmSession_updateSystemPromptNative(JNIEnv *env, jobject thiz,
                                                                        jlong llm_ptr,
                                                                        jstring system_promp_j) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(llm_ptr);
    const char *system_prompt_cstr = env->GetStringUTFChars(system_promp_j, nullptr);
    if (llm) {
        llm->setSystemPrompt(system_prompt_cstr);
    }
    env->ReleaseStringUTFChars(system_promp_j, system_prompt_cstr);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_alibaba_mnnllm_android_llm_LlmSession_updateAssistantPromptNative(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jlong llm_ptr,
                                                                           jstring assistant_prompt_j) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(llm_ptr);
    const char *assistant_prompt_cstr = env->GetStringUTFChars(assistant_prompt_j, nullptr);
    if (llm) {
        llm->SetAssistantPrompt(assistant_prompt_cstr);
    }
    env->ReleaseStringUTFChars(assistant_prompt_j, assistant_prompt_cstr);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_alibaba_mnnllm_android_llm_LlmSession_updateConfigNative(JNIEnv *env,
                                                                 jobject thiz,
                                                                 jlong llm_ptr,
                                                                 jstring config_json_j) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(llm_ptr);
    const char *config_json_cstr = env->GetStringUTFChars(config_json_j, nullptr);
    if (llm) {
        llm->updateConfig(config_json_cstr);
    }
    env->ReleaseStringUTFChars(config_json_j, config_json_cstr);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_alibaba_mnnllm_android_llm_LlmSession_getSystemPromptNative(JNIEnv *env, jobject thiz,
                                                                     jlong llm_ptr) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(llm_ptr);
    if (llm) {
        std::string system_prompt = llm->getSystemPrompt();
        return env->NewStringUTF(system_prompt.c_str());
    }
    return nullptr;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_alibaba_mnnllm_android_llm_LlmSession_clearHistoryNative(JNIEnv *env, jobject thiz,
                                                                  jlong llm_ptr) {
    auto *llm = reinterpret_cast<mls::LlmSession *>(llm_ptr);
    if (llm) {
        llm->clearHistory();
    }
}

} // extern "C"
