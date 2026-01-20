# ShaderX ProGuard rules

# Keep all public API classes
-keep class io.github.debanshu.shaderx.** { *; }
-keep class com.debanshu.shaderlab.shaderx.** { *; }

# Keep shader source code strings (used at runtime)
-keepclassmembers class * implements com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect {
    public java.lang.String getShaderSource();
}

# Keep effect IDs
-keepclassmembers class * implements com.debanshu.shaderlab.shaderx.effect.ShaderEffect {
    public java.lang.String getId();
    public java.lang.String getDisplayName();
}

