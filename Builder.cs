using UnityEngine;
using UnityEditor;
using System.Linq;
using System;
using System.IO;

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
        BuildPipeline.BuildPlayer(GetEnabledScenes(), "./Builds/", BuildTarget.WebGL, BuildOptions.None);
    }
}