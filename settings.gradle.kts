rootProject.name = "Tournament"

plugins {
    id("com.gradle.enterprise") version ("3.13.3")
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

includeBuild("D:\\projects\\MLib") {
    dependencySubstitution {
        substitute(module("mlib.api:MLib")).using(project(":"))
    }
}