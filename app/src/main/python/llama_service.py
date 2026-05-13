"""
Offline GGUF Model Service for ResQ
Handles model loading and text generation without internet
"""

import os
from pathlib import Path
from llama_cpp import Llama

# Global model instance
_model = None
_model_path = None

def initialize_model(model_file_path):
    """
    Initialize the GGUF model
    Args:
        model_file_path: Full path to the GGUF model file
    Returns:
        True if successful, False otherwise
    """
    global _model, _model_path
    
    try:
        if not os.path.exists(model_file_path):
            print(f"Model file not found: {model_file_path}")
            return False
        
        _model_path = model_file_path
        
        # Load model with quantization
        _model = Llama(
            model_path=model_file_path,
            n_ctx=512,  # Context size
            n_threads=4,  # CPU threads
            n_gpu_layers=0,  # Use CPU only (safer on Android)
            verbose=False
        )
        
        print(f"Model loaded successfully: {model_file_path}")
        return True
    except Exception as e:
        print(f"Error initializing model: {e}")
        _model = None
        return False

def generate_text(prompt, max_tokens=256, temperature=0.7):
    """
    Generate text using the loaded GGUF model
    Args:
        prompt: Input text
        max_tokens: Maximum tokens to generate
        temperature: Sampling temperature
    Returns:
        Generated text string
    """
    global _model
    
    if _model is None:
        raise RuntimeError("Model not initialized. Call initialize_model() first.")
    
    try:
        output = _model(
            prompt,
            max_tokens=max_tokens,
            temperature=temperature,
            top_p=0.95,
            top_k=40,
            repeat_penalty=1.1,
            echo=False
        )
        
        generated_text = output["choices"][0]["text"].strip()
        return generated_text
    except Exception as e:
        print(f"Error generating text: {e}")
        raise

def is_model_loaded():
    """Check if model is loaded"""
    return _model is not None

def get_model_info():
    """Get information about the loaded model"""
    if _model is None:
        return None
    return {
        "model_path": _model_path,
        "is_loaded": True
    }
