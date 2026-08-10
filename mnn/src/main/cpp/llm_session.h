//
// Created by ruoyi.sdj on 2025/4/18.
//
// Ported to RikkaLLM: audio (wavform) output and the benchmark helpers were
// removed together with their JNI entry points.
//
#pragma once
#include <vector>
#include <string>
#include <chrono>
#include "nlohmann/json.hpp"
#include "llm/llm.hpp"

// Forward declarations for JNI types
#ifdef __cplusplus
extern "C" {
#endif
typedef struct _JNIEnv JNIEnv;
typedef struct _jobject* jobject;
#ifdef __cplusplus
}
#endif

using nlohmann::json;
using MNN::Transformer::Llm;

namespace mls {
using PromptItem = std::pair<std::string, std::string>;

class LlmSession {
public:
    LlmSession(std::string, json config, json extra_config, std::vector<std::string> string_history);
    void Reset();
    bool Load();
    bool isModelReady() const { return llm_ != nullptr && model_loaded_; }
    /** Last error message when Load() fails. Cleared on success. */
    const std::string& getLastLoadError() const { return last_load_error_; }
    ~LlmSession();
    std::string getDebugInfo();
    const MNN::Transformer::LlmContext *
    Response(const std::string &prompt, const std::function<bool(const std::string &, bool is_eop)> &on_progress);
    void SetMaxNewTokens(int i);

    void setSystemPrompt(std::string system_prompt);

    void SetAssistantPrompt(const std::string& assistant_prompt);

    void updateConfig(const std::string& config_json);

    // Statelessly run inference over a complete caller-provided history
    const MNN::Transformer::LlmContext *
    ResponseWithHistory(const std::vector<PromptItem>& full_history,
                        const std::function<bool(const std::string &, bool is_eop)> &on_progress);

    std::string getSystemPrompt() const;

    void clearHistory(int numToKeep = 1);

    std::string dumpConfig() const;

private:
    std::string response_string_for_debug{};
    std::string model_path_;
    std::vector<PromptItem> history_{};
    json extra_config_{};
    json config_{};
    bool is_r1_{false};
    bool stop_requested_{false};
    bool generate_text_end_{false};
    bool keep_history_{true};
    Llm* llm_{nullptr};
    bool model_loaded_{false};
    std::string prompt_string_for_debug{};
    int max_new_tokens_{2048};
    std::string system_prompt_;
    json current_config_{};
    std::string last_load_error_{};
};
}
