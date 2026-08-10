#include "processor.h"

#include <algorithm>
#include <regex>
#include <utility>

#include "mls_log.h"

// Ported to RikkaLLM: <video> tag handling and VideoProcessor usage removed.

namespace mls {
PromptProcessor::PromptProcessor(PromptProcessorConfig config)
    : config_(std::move(config)) {
}

MNN::Express::VARP PromptProcessor::LoadImageFromPath(const std::string& image_path) {
    MNN_ERROR("Image loading not yet implemented for Android native path: %s", image_path.c_str());
    return nullptr;
}

std::string PromptProcessor::EscapeForRegex(const std::string& text) {
    std::string escaped;
    escaped.reserve(text.size() * 2);
    for (char c : text) {
        switch (c) {
            case '.':
            case '^':
            case '$':
            case '|':
            case '(':
            case ')':
            case '[':
            case ']':
            case '{':
            case '}':
            case '*':
            case '+':
            case '?':
            case '\\':
                escaped.push_back('\\');
                break;
            default:
                break;
        }
        escaped.push_back(c);
    }
    return escaped;
}

PromptProcessingResult PromptProcessor::Process(const std::string& prompt_text) const {
    PromptProcessingResult result;
    result.multimodal_prompt.prompt_template = prompt_text;

    ProcessorState state;
    state.final_prompt = prompt_text;

    if (prompt_text.find("<img>") == std::string::npos) {
        return result;
    }

    bool has_images = HandleImageTags(prompt_text, result, state);

    result.has_multimodal = has_images && (state.successful_loads > 0);
    result.multimodal_prompt.prompt_template = state.final_prompt;

    if (result.has_multimodal) {
        MNN_DEBUG("Processed multimodal prompt with %zu images (%d successful, %d failed)",
                  result.multimodal_prompt.images.size(), state.successful_loads, state.failed_loads);
    } else if (state.failed_loads > 0) {
        MNN_DEBUG("All multimodal content failed to load, falling back to text-only mode");
    }

    return result;
}

bool PromptProcessor::HandleImageTags(const std::string& prompt_text,
                                          PromptProcessingResult& result,
                                          ProcessorState& state) const {
    bool has_images = false;

    std::regex img_regex("<img>([^<]*)</img>");
    std::smatch match;
    auto search_start = prompt_text.cbegin();

    while (std::regex_search(search_start, prompt_text.cend(), match, img_regex)) {
        if (state.image_index >= config_.max_debug_images) {
            MNN_DEBUG("Reached MAX_DEBUG_IMAGES limit (%d), skipping remaining images", config_.max_debug_images);
            break;
        }

        std::string image_path = match[1].str();
        if (!image_path.empty()) {
            MNN_DEBUG("Found image tag with path: %s", image_path.c_str());

            auto image_var = LoadImageFromPath(image_path);
            if (image_var.get() != nullptr) {
                std::string image_key = "image_" + std::to_string(state.image_index);
                MNN::Transformer::PromptImagePart image_part;
                image_part.image_data = image_var;
                image_part.width = 0;
                image_part.height = 0;
                result.multimodal_prompt.images[image_key] = image_part;
                has_images = true;
                state.image_index++;
                state.successful_loads++;
                MNN_DEBUG("Successfully loaded image: %s as %s", image_path.c_str(), image_key.c_str());
            } else {
                state.failed_loads++;
                MNN_ERROR("Failed to load image from path: %s", image_path.c_str());
                result.error_message += "Failed to load image: " + image_path + "; ";
                const std::string escaped_path = EscapeForRegex(image_path);
                state.final_prompt = std::regex_replace(
                    state.final_prompt,
                    std::regex("<img>" + escaped_path + "</img>"),
                    ""
                );
            }
        }
        search_start = match.suffix().first;
    }

    return has_images;
}

} // namespace mls
