plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Device only: no simulator slice, nothing to download for one.
    iosArm64 {
        compilations.getByName("main") {
            cinterops {
                val oflux by creating {
                    defFile(project.file("src/nativeInterop/cinterop/oflux.def"))
                    includeDirs(rootProject.file("ios-app/Lib"))
                }
            }
        }
        binaries.framework {
            baseName = "OpenFluxUI"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
