# ShaderLib ProGuard rules

# Keep all public API classes
-keep class io.github.debanshu.shaderlib.** { *; }
-keep class com.debanshu.shaderlab.shaderlib.** { *; }

# Keep shader source code strings (used at runtime)
-keepclassmembers class * implements com.debanshu.shaderlab.shaderlib.effect.RuntimeShaderEffect {
    public java.lang.String getShaderSource();
}

# Keep effect IDs
-keepclassmembers class * implements com.debanshu.shaderlab.shaderlib.effect.ShaderEffect {
    public java.lang.String getId();
    public java.lang.String getDisplayName();
}

