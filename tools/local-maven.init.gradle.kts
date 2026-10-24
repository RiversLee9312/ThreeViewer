settingsEvaluated {
    pluginManagement.repositories {
        clear()
        listOf("google", "central", "plugins").forEach { name ->
            maven {
                url = uri("http://127.0.0.1:18765/$name"); isAllowInsecureProtocol = true
                if (name == "google") content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com.android.*")
                    includeGroupByRegex("com.google.testing.platform.*")
                    includeGroupByRegex("com.google.android.apps.*")
                    includeGroup("com.google.android.material")
                }
            }
        }
    }
    dependencyResolutionManagement.repositories {
        clear()
        listOf("google", "central").forEach { name ->
            maven {
                url = uri("http://127.0.0.1:18765/$name"); isAllowInsecureProtocol = true
                if (name == "google") content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com.android.*")
                    includeGroupByRegex("com.google.testing.platform.*")
                    includeGroupByRegex("com.google.android.apps.*")
                    includeGroup("com.google.android.material")
                }
            }
        }
    }
}
