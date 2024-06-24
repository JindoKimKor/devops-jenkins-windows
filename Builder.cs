using UnityEditor;
using System.Linq;
using UnityEditor.Build.Reporting;

public class Builder
{
    static string[] GetEnabledScenes()
    {
        return (
            from scene in EditorBuildSettings.scenes
            where scene.enabled
            where !string.IsNullOrEmpty(scene.path)
            select scene.path
        ).ToArray();
    }

    static void BuildWebGL()
    {
        PlayerSettings.WebGL.emscriptenArgs = "-O0 -s WASM_MEM_MAX=1024MB -s ALLOW_MEMORY_GROWTH=1 -s";

        BuildReport report = BuildPipeline.BuildPlayer(GetEnabledScenes(), "./Builds/", BuildTarget.WebGL, BuildOptions.None);

        if (report.summary.result == BuildResult.Succeeded) 
        { 
            EditorApplication.Exit(0); 
        }
        else 
        {
            EditorApplication.Exit(1);
        }
    }
}