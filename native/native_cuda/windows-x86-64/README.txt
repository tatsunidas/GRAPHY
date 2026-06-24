Cinematic Rendering (CUDA path) needs NVRTC's own redistributable DLLs here -
these are NOT part of the NVIDIA GPU driver, so end users who only have a
driver installed (the common case) won't have them. Without these two files
present, GLCanvas.createCinematicRenderer() falls back to the OpenGL path
automatically (CinematicGpuDetector logs why).

Place these two files from a CUDA Toolkit install (same version pair,
typically under "<CUDA install dir>\bin\"):
  - nvrtc64_<ver>_0.dll
  - nvrtc-builtins64_<ver>.dll

NVIDIA explicitly permits ISVs to redistribute these two files with their
application (see the CUDA Toolkit EULA's redistributable list).
