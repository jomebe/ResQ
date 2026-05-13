#include <jni.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <android/log.h>

#include "llama.h"

namespace {
    std::mutex g_mutex;
    llama_model * g_model = nullptr;
    llama_context * g_ctx = nullptr;
    int g_threads = 4;
    bool g_backend_inited = false;
    std::atomic<int64_t> g_abort_deadline_ms{0};

    int64_t now_ms() {
        using namespace std::chrono;
        return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
    }

    bool abort_when_timed_out(void *) {
        const int64_t deadline = g_abort_deadline_ms.load(std::memory_order_relaxed);
        return deadline > 0 && now_ms() >= deadline;
    }

    struct ScopedDecodeDeadline {
        explicit ScopedDecodeDeadline(int64_t timeout_ms) {
            g_abort_deadline_ms.store(now_ms() + timeout_ms, std::memory_order_relaxed);
        }

        ~ScopedDecodeDeadline() {
            g_abort_deadline_ms.store(0, std::memory_order_relaxed);
        }
    };

    void clear_state() {
        if (g_ctx) {
            llama_free(g_ctx);
            g_ctx = nullptr;
        }
        if (g_model) {
            llama_model_free(g_model);
            g_model = nullptr;
        }
    }

    std::string jstring_to_string(JNIEnv * env, jstring value) {
        if (value == nullptr) {
            return std::string();
        }
        const char * chars = env->GetStringUTFChars(value, nullptr);
        std::string out(chars ? chars : "");
        env->ReleaseStringUTFChars(value, chars);
        return out;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_resq_LlamaNative_init(JNIEnv * env, jobject /*thiz*/, jstring modelPath, jint nCtx, jint nThreads) {
    std::lock_guard<std::mutex> lock(g_mutex);

    clear_state();

    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    const std::string path = jstring_to_string(env, modelPath);
    if (path.empty()) {
        return JNI_FALSE;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) {
        clear_state();
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(nCtx);
    cparams.n_batch = static_cast<uint32_t>(std::min(nCtx, 256));
    cparams.n_ubatch = static_cast<uint32_t>(std::min(nCtx, 128));
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    cparams.abort_callback = abort_when_timed_out;
    cparams.abort_callback_data = nullptr;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        clear_state();
        return JNI_FALSE;
    }

    if (nThreads > 0) {
        g_threads = nThreads;
    } else {
        int hw = static_cast<int>(std::thread::hardware_concurrency());
        g_threads = hw > 0 ? hw : 1;
    }
    __android_log_print(ANDROID_LOG_DEBUG, "ResQ-LLM", "initialized model threads=%d n_ctx=%d", g_threads, nCtx);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_resq_LlamaNative_generate(JNIEnv * env, jobject /*thiz*/, jstring prompt, jint maxTokens, jfloat temperature) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_ctx || !g_model) {
        return env->NewStringUTF("");
    }

    const std::string prompt_str = jstring_to_string(env, prompt);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const ScopedDecodeDeadline deadline(maxTokens <= 8 ? 8000 : 9500);

    llama_set_n_threads(g_ctx, g_threads, g_threads);
    llama_memory_clear(llama_get_memory(g_ctx), true);
    llama_perf_context_reset(g_ctx);

    std::vector<llama_token> tokens(static_cast<size_t>(prompt_str.size()) + 8);
    int32_t n_tokens = llama_tokenize(vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()),
                                      tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (n_tokens < 0) {
        tokens.resize(static_cast<size_t>(-n_tokens));
        n_tokens = llama_tokenize(vocab, prompt_str.c_str(), static_cast<int32_t>(prompt_str.size()),
                                  tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (n_tokens <= 0) {
        return env->NewStringUTF("");
    }

    tokens.resize(static_cast<size_t>(n_tokens));
    const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(g_ctx));
    const int32_t max_prompt_tokens = std::max(1, n_ctx - std::max(4, static_cast<int>(maxTokens)) - 1);
    if (n_tokens > max_prompt_tokens) {
        std::vector<llama_token> trimmed;
        trimmed.reserve(static_cast<size_t>(max_prompt_tokens));
        trimmed.push_back(tokens.front());
        trimmed.insert(
            trimmed.end(),
            tokens.end() - (max_prompt_tokens - 1),
            tokens.end()
        );
        tokens.swap(trimmed);
        n_tokens = static_cast<int32_t>(tokens.size());
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(g_ctx, batch) != 0) {
        return env->NewStringUTF("");
    }

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    std::string output;
    const llama_token eos = llama_vocab_eos(vocab);
    const llama_token eot = llama_vocab_eot(vocab);

    int max_iter = std::max(4, maxTokens * 2);
    for (int i = 0; i < max_iter; ++i) {
        const llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
        if (token == eos || token == eot) {
            break;
        }
        if (token == -1 || token == 0) {
            break;
        }

        llama_sampler_accept(sampler, token);

        llama_batch token_batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(g_ctx, token_batch) != 0) {
            break;
        }

        char piece[128];
        const int32_t piece_len = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (piece_len > 0) {
            output.append(piece, static_cast<size_t>(piece_len));
        }
    }

    llama_sampler_free(sampler);

    __android_log_print(ANDROID_LOG_DEBUG, "ResQ-LLM", "generate finished output_len=%zu", output.size());

    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_resq_LlamaNative_close(JNIEnv * /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    clear_state();
}
