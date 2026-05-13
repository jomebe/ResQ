package com.example.resq

internal class LlamaNative {
    external fun init(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    external fun generate(prompt: String, maxTokens: Int, temperature: Float): String
    external fun close()

    companion object {
        init {
            System.loadLibrary("resqllama")
        }
    }
}
