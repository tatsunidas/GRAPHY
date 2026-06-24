Cinematic Rendering (CUDA path) needs NVRTC's own redistributable shared
libraries here - these are NOT part of the NVIDIA GPU driver, so end users
who only have a driver installed (the common case) won't have them. Without
these files present, GLCanvas.createCinematicRenderer() falls back to the
OpenGL path automatically (CinematicGpuDetector logs why).

Place these files from a CUDA Toolkit install (same version set, typically
under "<CUDA install dir>/lib64/"):
  - libnvrtc.so.<ver>  (plus its libnvrtc.so -> libnvrtc.so.<ver> symlink)
  - libnvrtc-builtins.so.<ver>

NVIDIA explicitly permits ISVs to redistribute these files with their
application (see the CUDA Toolkit EULA's redistributable list).
